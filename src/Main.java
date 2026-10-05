import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.nio.file.*;
import java.security.*;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.util.concurrent.*;
import java.io.*;
import javax.net.ssl.SSLSocketFactory;

/** Starts the private, local Baby Development Calendar website. */
public class Main {
    private static final int PORT = Integer.getInteger("port", 8080);
    private static BabyDataStore store;
    private static final Path USERS_FILE = Paths.get("baby-users.properties");
    private static final Map<String,String> sessions = new ConcurrentHashMap<>();
    private static final Properties users = new Properties();

    public static void main(String[] args) throws IOException {
        store = BabyDataStore.load();
        if (Files.exists(USERS_FILE)) try (var in=Files.newInputStream(USERS_FILE)) { users.load(in); }
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", PORT), 0);
        server.createContext("/", Main::handle);
        server.start();
        Executors.newSingleThreadScheduledExecutor(r -> { Thread t=new Thread(r,"email-reminders"); t.setDaemon(true); return t; }).scheduleAtFixedRate(Main::sendDueReminders, 30, 60, TimeUnit.SECONDS);
        System.out.println("Open http://localhost:" + PORT + " to use Baby Development Calendar.");
    }

    private static synchronized void handle(HttpExchange x) throws IOException {
        try {
            String userId = sessionUser(x);
            if ("POST".equalsIgnoreCase(x.getRequestMethod())) {
                Map<String,String> form=readForm(x); String action=form.getOrDefault("action","");
                if(action.equals("register") || action.equals("login")) { userId=authenticate(form,action); String token=UUID.randomUUID().toString(); sessions.put(token,userId); x.getResponseHeaders().add("Set-Cookie","BDC_SESSION="+token+"; Path=/; HttpOnly; SameSite=Strict"); }
                else if(action.equals("logout")) { String token=sessionToken(x); if(token!=null)sessions.remove(token); x.getResponseHeaders().add("Set-Cookie","BDC_SESSION=; Path=/; Max-Age=0; HttpOnly; SameSite=Strict"); userId=null; }
                else { if(userId==null) throw new IllegalArgumentException("Մուտք գործեք ձեր հաշվին"); store=loadUserStore(userId); save(form); }
                x.getResponseHeaders().set("Location", "/"); x.sendResponseHeaders(303, -1); return;
            }
            if(userId==null) { sendHtml(x,authPage()); return; }
            store=loadUserStore(userId);
            byte[] b = page().getBytes(StandardCharsets.UTF_8);
            x.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
            x.getResponseHeaders().set("Cache-Control", "no-store"); x.sendResponseHeaders(200, b.length); x.getResponseBody().write(b);
        } catch (IllegalArgumentException e) {
            byte[] b = ("Սխալ տվյալներ․ " + esc(e.getMessage()) + " — <a href='/'>Վերադառնալ</a>").getBytes(StandardCharsets.UTF_8);
            x.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8"); x.sendResponseHeaders(400, b.length); x.getResponseBody().write(b);
        } catch (Exception e) {
            byte[] b = ("Սխալ՝ " + esc(e.getMessage())).getBytes(StandardCharsets.UTF_8);
            x.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8"); x.sendResponseHeaders(500, b.length); x.getResponseBody().write(b);
        } finally { x.close(); }
    }

    private static String sessionToken(HttpExchange x) { String c=x.getRequestHeaders().getFirst("Cookie"); if(c==null)return null; for(String part:c.split(";")){String s=part.trim();if(s.startsWith("BDC_SESSION="))return s.substring(12);}return null; }
    private static String sessionUser(HttpExchange x){String t=sessionToken(x);return t==null?null:sessions.get(t);}
    private static void sendHtml(HttpExchange x,String html)throws IOException{byte[]b=html.getBytes(StandardCharsets.UTF_8);x.getResponseHeaders().set("Content-Type","text/html; charset=utf-8");x.getResponseHeaders().set("Cache-Control","no-store");x.sendResponseHeaders(200,b.length);x.getResponseBody().write(b);}
    private static BabyDataStore loadUserStore(String id)throws IOException{return BabyDataStore.load(Paths.get("baby-data",id+".properties"));}
    private static void persistUsers()throws IOException{try(var out=Files.newOutputStream(USERS_FILE)){users.store(out,"Baby Calendar accounts");}}
    private static String authenticate(Map<String,String> f,String action)throws Exception{
        String email=req(f,"email").toLowerCase(Locale.ROOT), password=req(f,"password");
        if(!email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"))throw new IllegalArgumentException("Մուտքագրեք վավեր էլ․ հասցե");
        String id=null;
        for(String k:users.stringPropertyNames())if(k.endsWith(".email")&&users.getProperty(k).equals(email))id=k.substring(0,k.length()-6);
        if(action.equals("register")){
            if(id!=null)throw new IllegalArgumentException("Այս էլ․ հասցեն արդեն գրանցված է");
            if(password.length()<8)throw new IllegalArgumentException("Գաղտնաբառը պետք է ունենա առնվազն 8 նիշ");
            id=UUID.randomUUID().toString();byte[] salt=new byte[16];new SecureRandom().nextBytes(salt);
            users.setProperty(id+".email",email);users.setProperty(id+".salt",Base64.getEncoder().encodeToString(salt));users.setProperty(id+".hash",passwordHash(password,salt));persistUsers();
        } else {
            if(id==null)throw new IllegalArgumentException("Էլ․ հասցեն կամ գաղտնաբառը սխալ է");byte[] salt=Base64.getDecoder().decode(users.getProperty(id+".salt"));
            if(!MessageDigest.isEqual(passwordHash(password,salt).getBytes(StandardCharsets.UTF_8),users.getProperty(id+".hash").getBytes(StandardCharsets.UTF_8)))throw new IllegalArgumentException("Էլ․ հասցեն կամ գաղտնաբառը սխալ է");
        }
        return id;
    }
    private static String passwordHash(String password,byte[]salt)throws Exception{PBEKeySpec spec=new PBEKeySpec(password.toCharArray(),salt,210000,256);try{return Base64.getEncoder().encodeToString(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded());}finally{spec.clearPassword();}}
    private static String authPage(){return "<!doctype html><html lang='hy'><meta charset='utf-8'><meta name='viewport' content='width=device-width'><title>Մուտք</title><style>body{font:16px system-ui;background:#f6f4f1;color:#34313e;margin:0}.wrap{max-width:850px;margin:8vh auto;padding:20px;display:grid;grid-template-columns:1fr 1fr;gap:20px}.card{background:white;border-radius:18px;padding:24px}h1{color:#714f74}form{display:grid;gap:12px}input,button{font:inherit;padding:12px;border:1px solid #ddd;border-radius:9px}button{background:#765477;color:white;border:0;cursor:pointer}@media(max-width:650px){.wrap{grid-template-columns:1fr}}</style><main class='wrap'><section><h1>🌱 Մանկիկի օրացույց</h1><p>Գրանցվեք՝ ձեր տվյալների և հիշեցումների անձնական էջ ունենալու համար։</p></section><section class='card'><h2>Մուտք</h2><form method='post'><input type='hidden' name='action' value='login'><input type='email' name='email' placeholder='Էլ․ հասցե' required><input type='password' name='password' placeholder='Գաղտնաբառ' required><button>Մուտք գործել</button></form><h2>Գրանցում</h2><form method='post'><input type='hidden' name='action' value='register'><input type='email' name='email' placeholder='Էլ․ հասցե' required><input type='password' name='password' placeholder='Գաղտնաբառ՝ առնվազն 8 նիշ' minlength='8' required><button>Գրանցվել</button></form></section></main></html>";}
    private static synchronized void sendDueReminders(){
        String host=System.getenv("BDC_SMTP_HOST"), from=System.getenv("BDC_SMTP_FROM"), pass=System.getenv("BDC_SMTP_PASSWORD");
        if(host==null||from==null||pass==null)return;
        int port=Integer.parseInt(System.getenv().getOrDefault("BDC_SMTP_PORT","465"));
        String user=System.getenv().getOrDefault("BDC_SMTP_USER",from);
        try {
            for(String key:users.stringPropertyNames()) if(key.endsWith(".email")) {
                String id=key.substring(0,key.length()-6), recipient=users.getProperty(key);
                BabyDataStore account=loadUserStore(id); Properties d=account.properties();
                for(int i=0;i<count(d,"reminders.count");i++) {
                    String p="reminder."+i+".", raw=d.getProperty(p+"dateTime"); if(raw==null)continue;
                    LocalDateTime at=LocalDateTime.parse(raw); LocalDate today=LocalDate.now(), eventDate=at.toLocalDate();
                    if(today.isBefore(eventDate.minusDays(3))||today.isAfter(eventDate)||today.toString().equals(d.getProperty(p+"sentOn")))continue;
                    String title=d.getProperty(p+"title","Հիշեցում"), type=d.getProperty(p+"type","Այլ");
                    String subject="Հիշեցում՝ "+title;
                    String body="Հիշեցում՝ "+title+"\nՎերաբերում է՝ "+type+"\nԱմսաթիվ և ժամ՝ "+at.format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))+"\n\nԱյս նամակն ուղարկվել է ձեր Baby Development Calendar հաշվից։";
                    smtpSend(host,port,user,pass,from,recipient,subject,body);
                    d.setProperty(p+"sentOn",today.toString()); account.save();
                }
            }
        } catch(Exception e) { System.err.println("Հիշեցման նամակը չուղարկվեց․ "+e.getMessage()); }
    }
    private static void smtpSend(String host,int port,String user,String password,String from,String to,String subject,String body)throws Exception {
        SSLSocketFactory ssl=(SSLSocketFactory)SSLSocketFactory.getDefault();
        java.net.Socket socket=port==465?ssl.createSocket(host,port):new java.net.Socket(host,port);
        try {
            socket.setSoTimeout(15000); BufferedReader in=new BufferedReader(new InputStreamReader(socket.getInputStream(),StandardCharsets.UTF_8));
            BufferedWriter out=new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(),StandardCharsets.UTF_8)); smtpExpect(in,220);
            smtpCommand(out,in,"EHLO localhost",250);
            if(port!=465){smtpCommand(out,in,"STARTTLS",220);socket=ssl.createSocket(socket,host,port,true);socket.setSoTimeout(15000);in=new BufferedReader(new InputStreamReader(socket.getInputStream(),StandardCharsets.UTF_8));out=new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(),StandardCharsets.UTF_8));smtpCommand(out,in,"EHLO localhost",250);}
            smtpCommand(out,in,"AUTH LOGIN",334);smtpCommand(out,in,Base64.getEncoder().encodeToString(user.getBytes(StandardCharsets.UTF_8)),334);smtpCommand(out,in,Base64.getEncoder().encodeToString(password.getBytes(StandardCharsets.UTF_8)),235);
            smtpCommand(out,in,"MAIL FROM:<"+from+">",250);smtpCommand(out,in,"RCPT TO:<"+to+">",250);smtpCommand(out,in,"DATA",354);
            String encodedSubject=Base64.getEncoder().encodeToString(subject.getBytes(StandardCharsets.UTF_8));
            out.write("From: "+from+"\r\nTo: "+to+"\r\nSubject: =?UTF-8?B?"+encodedSubject+"?=\r\nMIME-Version: 1.0\r\nContent-Type: text/plain; charset=UTF-8\r\nContent-Transfer-Encoding: base64\r\n\r\n");
            out.write(Base64.getMimeEncoder(72,new byte[]{13,10}).encodeToString(body.getBytes(StandardCharsets.UTF_8))+"\r\n.\r\n");out.flush();smtpExpect(in,250);smtpCommand(out,in,"QUIT",221);
        } finally { socket.close(); }
    }
    private static void smtpCommand(BufferedWriter out,BufferedReader in,String command,int expected)throws IOException{out.write(command+"\r\n");out.flush();smtpExpect(in,expected);}
    private static void smtpExpect(BufferedReader in,int expected)throws IOException{String line;int code;do{line=in.readLine();if(line==null||line.length()<3)throw new IOException("SMTP կապը փակվեց");try{code=Integer.parseInt(line.substring(0,3));}catch(NumberFormatException e){throw new IOException("SMTP-ի սխալ պատասխան");}}while(line.length()>3&&line.charAt(3)=='-');if(code!=expected)throw new IOException("SMTP պատասխան՝ "+line);}

    private static Map<String,String> readForm(HttpExchange x) throws IOException {
        String raw = new String(x.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        return Arrays.stream(raw.split("&")).map(s -> s.split("=",2)).filter(a -> a.length == 2)
                .collect(Collectors.toMap(a -> dec(a[0]), a -> dec(a[1]), (a,b) -> b));
    }
    private static String dec(String s) { return URLDecoder.decode(s, StandardCharsets.UTF_8); }
    private static String req(Map<String,String> f,String k) { String v=f.getOrDefault(k,"").trim(); if(v.isEmpty()) throw new IllegalArgumentException("լրացրեք բոլոր դաշտերը"); return v; }
    private static int count(Properties p,String k) { return Integer.parseInt(p.getProperty(k,"0")); }
    private static double positive(Map<String,String> f,String k) {
        try { double v=Double.parseDouble(req(f,k).replace(',','.')); if(v>0 && Double.isFinite(v)) return v; } catch(NumberFormatException ignored){}
        throw new IllegalArgumentException("քաշն ու հասակը պետք է դրական թվեր լինեն");
    }
    private static void save(Map<String,String> f) throws IOException {
        Properties d=store.properties();
        switch(f.getOrDefault("action","")) {
            case "profile": {
                String name=req(f,"name"); LocalDate birth=LocalDate.parse(req(f,"birthDate"));
                if(birth.isAfter(LocalDate.now())) throw new IllegalArgumentException("ծննդյան ամսաթիվը չի կարող ապագայում լինել");
                store.set("baby.name",name); store.set("baby.birthDate",birth.toString());
                String sex=f.getOrDefault("sex","unknown"); if(!Arrays.asList("boy","girl","unknown").contains(sex))throw new IllegalArgumentException("Ընտրեք սեռի տարբերակը");store.set("baby.sex",sex);
                String country=f.getOrDefault("country","AM");store.set("baby.country",country.equals("AM")?"AM":"other"); break;
            }
            case "reminder": {
                LocalDateTime t=LocalDateTime.parse(req(f,"dateTime")); if(t.isBefore(LocalDateTime.now())) throw new IllegalArgumentException("հիշեցման ժամը պետք է ապագայում լինի");
                int n=count(d,"reminders.count"); String p="reminder."+n+"."; d.setProperty(p+"type",req(f,"type")); d.setProperty(p+"title",req(f,"title")); d.setProperty(p+"dateTime",t.toString()); d.setProperty("reminders.count",""+(n+1)); break;
            }
            case "deleteReminder": {
                int ix=Integer.parseInt(req(f,"index")), n=count(d,"reminders.count"); if(ix<0||ix>=n) throw new IllegalArgumentException("հիշեցումը չի գտնվել");
                List<String[]> a=new ArrayList<>(); for(int i=0;i<n;i++){String p="reminder."+i+"."; a.add(new String[]{d.getProperty(p+"type","Այլ"),d.getProperty(p+"title",""),d.getProperty(p+"dateTime","")}); d.remove(p+"type");d.remove(p+"title");d.remove(p+"dateTime");} a.sort(Comparator.comparing(r->r[2]));
                a.remove(ix); for(int i=0;i<a.size();i++){String p="reminder."+i+".";d.setProperty(p+"type",a.get(i)[0]);d.setProperty(p+"title",a.get(i)[1]);d.setProperty(p+"dateTime",a.get(i)[2]);} d.setProperty("reminders.count",""+a.size()); break;
            }
            case "growth": {
                LocalDate date=LocalDate.parse(req(f,"date")); if(date.isAfter(LocalDate.now())) throw new IllegalArgumentException("չափման ամսաթիվը չի կարող ապագայում լինել");
                double w=positive(f,"weight"),h=positive(f,"height"); int n=count(d,"growth.count"); String p="growth."+n+".";
                d.setProperty(p+"date",date.toString());d.setProperty(p+"weightKg",""+w);d.setProperty(p+"heightCm",""+h);d.setProperty("growth.count",""+(n+1));break;
            }
            case "note": { int n=count(d,"notes.count");String p="note."+n+".";d.setProperty(p+"date",LocalDate.now().toString());d.setProperty(p+"text",req(f,"text"));d.setProperty("notes.count",""+(n+1));break; }
            default: throw new IllegalArgumentException("անհայտ գործողություն");
        }
        store.save();
    }
    private static List<String[]> reminders() {
        Properties d=store.properties();List<String[]> a=new ArrayList<>();for(int i=0;i<count(d,"reminders.count");i++){String p="reminder."+i+".";if(d.getProperty(p+"dateTime")!=null)a.add(new String[]{d.getProperty(p+"type","Այլ"),d.getProperty(p+"title",""),d.getProperty(p+"dateTime")});}
        a.sort(Comparator.comparing(r->r[2]));return a;
    }
    private static String page() {
        Properties d=store.properties();String name=d.getProperty("baby.name",""),birth=d.getProperty("baby.birthDate",""),sex=d.getProperty("baby.sex","unknown"),country=d.getProperty("baby.country","AM");LocalDate today=LocalDate.now();int months=0;String age="";
        if(!birth.isEmpty()){Period p=Period.between(LocalDate.parse(birth),today);months=p.getYears()*12+p.getMonths();age=p.getYears()+" տ. "+p.getMonths()+" ամս. "+p.getDays()+" օր";}
        StringBuilder h=new StringBuilder("<!doctype html><html lang='hy'><head><meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1'><title>Մանկիկի օրացույց</title><style>");
        h.append("*{box-sizing:border-box}body{margin:0;background:#f6f4f1;color:#34313e;font:16px system-ui,sans-serif}header{background:white;padding:20px max(20px,calc((100% - 1060px)/2));border-bottom:1px solid #eee;color:#714f74;font-size:21px;font-weight:bold}.wrap{max-width:1060px;margin:28px auto;padding:0 18px}.hero{background:linear-gradient(120deg,#f1e8f1,#fef2e8);padding:28px;border-radius:20px;margin-bottom:20px}.grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(290px,1fr));gap:17px}.card{background:white;border-radius:17px;padding:21px;box-shadow:0 6px 24px #49314b0b}.card h2{margin:0 0 14px;font-size:19px}form{display:grid;gap:10px;margin:10px 0}input,select,textarea,button{font:inherit;padding:10px 12px;border:1px solid #ddd;border-radius:9px;background:white;width:100%}textarea{min-height:76px}button{background:#765477;color:white;border:0;cursor:pointer}label{display:grid;gap:5px}.muted{color:#777}.tag{display:inline-block;background:#fff;border-radius:16px;padding:7px 11px;margin:6px 5px 0 0}.row{display:flex;gap:10px;align-items:center;justify-content:space-between;border-top:1px solid #eee;padding:10px 0}.row form{margin:0}.row button{width:auto;background:#eee;color:#554;padding:6px 9px}footer{text-align:center;color:#888;padding:24px;font-size:13px}.med-backdrop{position:fixed;inset:0;background:#211a24aa;z-index:5;display:none;align-items:center;justify-content:center;padding:16px}.med-modal{background:#fff;border-radius:18px;padding:24px;max-width:760px;width:100%;max-height:90vh;overflow:auto;box-shadow:0 20px 70px #0004}.med-modal h2{color:#714f74}.med-modal li{margin:7px 0}.med-close{position:sticky;top:0;float:right;width:auto}.med-alert{padding:13px;background:#fff3dc;border-radius:10px}.med-modal a{color:#68456b}@media(max-width:600px){.med-modal{padding:18px;max-height:94vh}}");
        h.append("</style></head><body><header style='display:flex;justify-content:space-between;align-items:center'>🌱 Մանկիկի օրացույց<form method='post' style='margin:0'><input type='hidden' name='action' value='logout'><button style='width:auto'>Դուրս գալ</button></form></header><main class='wrap'><section class='hero'><h1>").append(name.isEmpty()?"Բարի գալուստ":esc(name)+"ի օրացույցը").append("</h1>");
        if(birth.isEmpty())h.append("<p>Սկսելու համար ավելացրեք փոքրիկի տվյալները։</p>");else h.append("<p>Ծննդյան օր՝ ").append(esc(birth)).append(" · Տարիք՝ ").append(esc(age)).append("</p><span class='tag'>🥣 Սնուցման ուղեցույց՝ ըստ տարիքի</span><span class='tag'>🌙 Քնի ուղեցույց՝ ըստ տարիքի</span><span class='tag'>✨ Զարգացման փուլ՝ ").append(months).append(" ամս.</span>");
        if(!birth.isEmpty())h.append("<p><button type='button' style='width:auto' onclick=\"showMedical()\">Բացել տարիքային բժշկական տեղեկությունները</button></p>");
        h.append("</section><div class='grid'><section class='card'><h2>👶 Փոքրիկի տվյալները</h2><form method='post'><input type='hidden' name='action' value='profile'><label>Անուն<input name='name' required value='").append(esc(name)).append("'></label><label>Ծննդյան ամսաթիվ<input type='date' name='birthDate' required value='").append(esc(birth)).append("'></label><label>Քաշի աճի գրաֆիկի սեռային տարբերակ<select name='sex'><option value='unknown' ").append(sex.equals("unknown")?"selected":"").append(">Չնշել</option><option value='boy' ").append(sex.equals("boy")?"selected":"").append(">Տղա</option><option value='girl' ").append(sex.equals("girl")?"selected":"").append(">Աղջիկ</option></select></label><label>Պատվաստումների երկիր<select name='country'><option value='AM' ").append(country.equals("AM")?"selected":"").append(">Հայաստան</option><option value='other' ").append(!country.equals("AM")?"selected":"").append(">Այլ երկիր</option></select></label><button>Պահպանել</button></form></section>");
        h.append("<section class='card'><h2>⏰ Ավելացնել հիշեցում</h2><form method='post'><input type='hidden' name='action' value='reminder'><select name='type'><option>Կերակրում</option><option>Քուն</option><option>Բժշկի այց</option><option>Այլ</option></select><input name='title' placeholder='Վերնագիր' required><input type='datetime-local' name='dateTime' required><button>Ավելացնել</button></form></section><section class='card'><h2>📅 Հիշեցումներ</h2>");
        List<String[]> rs=reminders();if(rs.isEmpty())h.append("<p class='muted'>Հիշեցումներ դեռ չկան։</p>");for(int i=0;i<rs.size();i++){String[] r=rs.get(i);h.append("<div class='row'><span><b>").append(esc(r[1])).append("</b><br><small class='muted'>").append(esc(r[0])).append(" · ").append(esc(LocalDateTime.parse(r[2]).format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")))).append("</small></span><form method='post'><input type='hidden' name='action' value='deleteReminder'><input type='hidden' name='index' value='").append(i).append("'><button>Ջնջել</button></form></div>");}
        h.append("</section><section class='card'><h2>📏 Աճի գրանցում</h2><form method='post'><input type='hidden' name='action' value='growth'><label>Ամսաթիվ<input type='date' name='date' required value='").append(today).append("'></label><label>Քաշ (կգ)<input type='number' step='0.01' min='0.01' name='weight' required></label><label>Հասակ (սմ)<input type='number' step='0.1' min='0.1' name='height' required></label><button>Գրանցել</button></form>");
        int gc=count(d,"growth.count");if(gc==0)h.append("<p class='muted'>Չափումներ դեռ չկան։</p>");for(int i=gc-1;i>=0&&i>=gc-5;i--){String p="growth."+i+".";h.append("<div class='row'><span>").append(esc(d.getProperty(p+"date"))).append("</span><span>").append(esc(d.getProperty(p+"weightKg"))).append(" կգ · ").append(esc(d.getProperty(p+"heightCm"))).append(" սմ</span></div>");}
        h.append("</section><section class='card'><h2>📝 Օրվա նշումներ</h2><form method='post'><input type='hidden' name='action' value='note'><textarea name='text' placeholder='Գրեք ձեր նշումը…' required></textarea><button>Պահպանել նշումը</button></form>");
        int nc=count(d,"notes.count");if(nc==0)h.append("<p class='muted'>Նշումներ դեռ չկան։</p>");for(int i=nc-1;i>=0&&i>=nc-5;i--){String p="note."+i+".";h.append("<div class='row'><span><small class='muted'>").append(esc(d.getProperty(p+"date"))).append("</small><br>").append(esc(d.getProperty(p+"text"))).append("</span></div>");}
        h.append("</section></div><footer>Տվյալները պահպանվում են այս սարքի առանձին հաշվային ֆայլում։</footer></main>");
        if(!birth.isEmpty())h.append(medicalPanel(months,sex,country,d,birth));
        return h.append("</body></html>").toString();
    }
    private static String medicalPanel(int months,String sex,String country,Properties d,String birth) {
        Period age=Period.between(LocalDate.parse(birth),LocalDate.now());
        String ageText=age.getYears()+" տ. "+age.getMonths()+" ամս. "+age.getDays()+" օր";
        StringBuilder m=new StringBuilder("<div class='med-backdrop' id='medicalModal' role='dialog' aria-modal='true' aria-labelledby='medicalTitle'><section class='med-modal'><button class='med-close' onclick='hideMedical()'>Փակել ✕</button><h2 id='medicalTitle'>Տարիքային առողջության ուղեցույց</h2><p>Տարիքը՝ ").append(esc(ageText)).append("</p>");
        m.append("<div class='med-alert'><b>Կարևոր․</b> սա ընդհանուր տեղեկություն է, ոչ թե անհատական բժշկական խորհուրդ կամ ախտորոշում։ Վաղաժամ ծննդի, հիվանդության կամ աճի մտահոգության դեպքում հետևեք մանկաբույժի ցուցումներին։</div>");
        m.append("<h3>⚖️ Քաշն ու աճը</h3><p>Միայն տարիքից ելնելով հնարավոր չէ ասել, թե երեխան «պետք է» կոնկրետ քանի կգ լինի։ Աճը գնահատվում է սեռին, ճշգրիտ տարիքին, հասակին և նախորդ չափումների ընթացքին համապատասխան գրաֆիկով։ Մեկ չափումը կամ միջին թիվը ինքնուրույն նորմա չի սահմանում։</p>");
        int gc=count(d,"growth.count"); if(gc>0){String p="growth."+(gc-1)+".";m.append("<p><b>Վերջին գրանցված չափումը՝</b> ").append(esc(d.getProperty(p+"weightKg"))).append(" կգ, ").append(esc(d.getProperty(p+"heightCm"))).append(" սմ՝ ").append(esc(d.getProperty(p+"date"))).append("։</p>");}else m.append("<p>Գրանցեք քաշն ու հասակը «Աճի գրանցում» բաժնում, ապա քննարկեք աճի գրաֆիկը մանկաբույժի հետ։</p>");
        m.append("<p>ԱՀԿ քաշ/տարիք գրաֆիկները (0–5 տ.)՝ ");
        if(sex.equals("boy"))m.append("<a target='_blank' rel='noopener' href='https://www.who.int/docs/default-source/child-growth/child-growth-standards/indicators/weight-for-age/cht-wfa-boys-z-0-5.pdf'>տղաների գրաֆիկը</a>");
        else if(sex.equals("girl"))m.append("<a target='_blank' rel='noopener' href='https://www.who.int/docs/default-source/child-growth/child-growth-standards/indicators/weight-for-age/cht-wfa-girls-z-0-5.pdf'>աղջիկների գրաֆիկը</a>");
        else m.append("<a target='_blank' rel='noopener' href='https://www.who.int/tools/child-growth-standards/standards/weight-for-age'>ընտրեք համապատասխան սեռի գրաֆիկը</a>");
        m.append("։ Հասակից կախված թերսնուցման գնահատման համար մանկաբույժը կարող է դիտարկել նաև քաշ/հասակ գրաֆիկը։</p>");
        if(months<=60 && !sex.equals("unknown")) m.append("<p><b>ԱՀԿ-ի միջին քաշային հղման արժեքը՝ ").append(whoMedian(sex,months)).append(" կգ</b>՝ ").append(months).append(" լրացած ամսական տարիքի համար։ Սա բնակչության միջին արժեք է, ոչ թե ձեր երեխայի քաշի նպատակ կամ առողջության գնահատական։ Մանկաբույժը հաշվի է առնում նաև հասակը, վաղաժամ ծնված լինելը և աճի ընթացքը։</p>");
        else if(months<=60) m.append("<p>Քաշի միջին հղման թիվ ցույց տալու համար ընտրեք սեռը վերևի տվյալների բաժնում։ Սեռը չնշելու դեպքում օգտվեք երկու ԱՀԿ գրաֆիկներից։</p>");
        else m.append("<p>5 տարեկանից բարձր երեխաների համար դիտեք ԱՀԿ-ի <a target='_blank' rel='noopener' href='https://www.who.int/tools/growth-reference-data-for-5to19-years/indicators/weight-for-age-5to10-years'>քաշ/տարիք 5–10 տարեկանների հղումները</a>։ 10 տարեկանից հետո քաշ/տարիք առանձին ցուցանիշը բավարար չէ․ անհրաժեշտ է մասնագիտական գնահատում։</p>");
        m.append("<h3>🌙 Քունը՝ ընդհանուր օրական տևողություն, ներառյալ ցերեկային քունը</h3><ul>");
        if(months<4)m.append("<li>0–3 ամսական՝ 14–17 ժամ</li>");else if(months<12)m.append("<li>4–12 ամսական՝ 12–16 ժամ</li>");else if(months<36)m.append("<li>1–2 տարեկան՝ 11–14 ժամ</li>");else if(months<72)m.append("<li>3–5 տարեկան՝ 10–13 ժամ</li>");else m.append("<li>5 տարեկանից բարձր երեխաների համար տարիքային քնի չափաբաժինը տարբեր է․ դիտեք CDC-ի աղյուսակը։</li>");
        m.append("</ul><p>Սրանք քնի առաջարկվող միջակայքներ են, ոչ թե խիստ ժամային գրաֆիկ։ <a target='_blank' rel='noopener' href='https://www.cdc.gov/sleep/about/'>CDC՝ քնի տևողությունն ըստ տարիքի</a></p>");
        m.append("<h3>🥣 Սնուցում</h3>");
        if(months<6)m.append("<p>Մինչև մոտ 6 ամսական՝ կրծքի կաթ կամ մանկական խառնուրդ՝ ըստ երեխայի քաղցի և հագեցման ազդակների։ Լրացուցիչ սնունդը սովորաբար սկսվում է մոտ 6 ամսականում, երբ երեխան պատրաստ է զարգացման առումով. մինչև 4 ամսականը խորհուրդ չի տրվում։</p>");
        else if(months<12)m.append("<p>Շարունակեք կրծքի կաթը կամ մանկական խառնուրդը։ Մոտ 6 ամսականից աստիճանաբար առաջարկեք տարիքին համապատասխան լրացուցիչ սնունդ՝ երեխայի պատրաստ լինելու դեպքում։ Կովի կաթը որպես հիմնական ըմպելիք մինչև 12 ամսականը խորհուրդ չի տրվում։</p>");
        else if(months<24)m.append("<p>Առաջարկեք բազմազան, անվտանգ կտրվածքով ընտանեկան սնունդ։ 6–24 ամսականների համար կարող է հարմար լինել օրական մոտ 3 հիմնական սնունդ և 2–3 փոքր սնունդ՝ ըստ ախորժակի և ընտանեկան ռեժիմի։</p>");
        else m.append("<p>Առաջարկեք տարիքին համապատասխան բազմազան սնունդ և կանոնավոր ընտանեկան սննդի ժամեր։ Պահանջվող քանակը կախված է երեխայից․ մի ստիպեք ուտել և անհատական հարցերը քննարկեք մանկաբույժի հետ։</p>");
        m.append("<p><a target='_blank' rel='noopener' href='https://www.cdc.gov/infant-toddler-nutrition/foods-and-drinks/when-what-and-how-to-introduce-solid-foods.html'>CDC՝ լրացուցիչ սնունդ ներմուծելու ուղեցույց</a></p>");
        m.append("<h3>💉 Պատվաստումների օրացույց</h3>");
        if(!country.equals("AM"))m.append("<p>Պատվաստումների օրացույցը երկրից երկիր տարբեր է։ Ընտրեք ձեր բնակության երկրի օրացույցը և ճշտեք մանկաբույժի կամ տեղական առողջապահական ծառայության հետ։</p>");
        else {
            m.append("<p>ՀՀ 2026–2030 ազգային ծրագրի տարիքային փուլերը։ Սա օրացույցի ընդհանուր ցանկն է, ոչ թե երեխայի պատվաստումների գրանցամատյանը․ ստուգեք պատվաստման վկայականը և բաց թողնված դեղաչափերը ճշտեք բժշկի հետ։</p><ul>");
            m.append("<li>Ծնվելուց հետո՝ հեպատիտ Բ (0–24 ժամ), ԲՑԺ (0–48 ժամ)</li><li>6, 12 և 18 շաբաթական՝ համակցված ԱԿԴՓ/ՎՀԲ/ՀԻԲ/ԻՊՊ, պնևմոկոկային․ ռոտավիրուսային՝ ըստ պատվաստանյութի սահմանափակումների</li><li>12 ամսական՝ ԿԿԽ և ջրծաղիկ</li><li>18 ամսական՝ ԱԿԴՓ/ԻՊՊ</li><li>4–6 տարեկան՝ ԱԿԴՓ/ԻՊՊ և ԿԿԽ երկրորդ դեղաչափ․ ջրծաղիկի երկրորդ դեղաչափը՝ 2027-ից</li></ul>");
            m.append("<p><a target='_blank' rel='noopener' href='https://www.arlis.am/hy/acts/219582'>ՀՀ իրավական տեղեկատվական համակարգ՝ 2026–2030 ազգային օրացույց</a></p>");
        }
        m.append("<p class='med-alert'>Պատվաստումների անհատական ժամկետը կախված է արդեն ստացած դեղաչափերից, հակացուցումներից և բժշկի գնահատումից։ Այս պատուհանը չի կարող որոշել՝ երեխան ինչ է արդեն ստացել կամ ինչ դեղաչափ է բաց թողել։</p><button onclick='hideMedical()'>Հասկացա</button></section></div><script>function showMedical(){document.getElementById('medicalModal').style.display='flex'}function hideMedical(){document.getElementById('medicalModal').style.display='none'}(function(){let k='med-"+esc(birth)+"-"+months+"-"+sex+"-"+country+"';if(!localStorage.getItem(k)){showMedical();localStorage.setItem(k,'1')}})();</script>");
        return m.toString();
    }
    private static double whoMedian(String sex,int months) {
        double[] boys={3.3,4.5,5.6,6.4,7.0,7.5,7.9,8.3,8.6,8.9,9.2,9.4,9.6,9.9,10.1,10.3,10.5,10.7,10.9,11.1,11.3,11.5,11.8,12.0,12.2,12.4,12.5,12.7,12.9,13.1,13.3,13.5,13.7,13.8,14.0,14.2,14.3,14.5,14.7,14.8,15.0,15.2,15.3,15.5,15.7,15.8,16.0,16.2,16.3,16.5,16.7,16.8,17.0,17.2,17.3,17.5,17.7,17.8,18.0,18.2,18.3};
        double[] girls={3.2,4.2,5.1,5.8,6.4,6.9,7.3,7.6,7.9,8.2,8.5,8.7,8.9,9.2,9.4,9.6,9.8,10.0,10.2,10.4,10.6,10.9,11.1,11.3,11.5,11.7,11.9,12.1,12.3,12.5,12.7,12.9,13.1,13.3,13.5,13.7,13.9,14.0,14.2,14.4,14.6,14.8,15.0,15.2,15.3,15.5,15.7,15.9,16.1,16.3,16.4,16.6,16.8,17.0,17.2,17.3,17.5,17.7,17.9,18.0,18.2};
        return (sex.equals("boy")?boys:girls)[Math.max(0,Math.min(months,60))];
    }
    private static String esc(String s){if(s==null)return "";return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&#39;");}
}
