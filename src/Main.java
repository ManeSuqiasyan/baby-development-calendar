import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;
import java.util.Scanner;

public class Main {
    private static final DateTimeFormatter DATE_TIME_INPUT =
            DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm");
    private static final DateTimeFormatter DATE_TIME_OUTPUT =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    public static void main(String[] args) {
        try (Scanner scanner = new Scanner(System.in)) {
            BabyDataStore store = BabyDataStore.load();
            Baby baby = getOrCreateBaby(scanner, store);

            System.out.println("\nՏվյալները պահվում են այս համակարգչում՝ baby-calendar-data.properties ֆայլում։");
            showDashboard(baby, store);

            boolean running = true;
            while (running) {
                printMenu();
                String choice = scanner.nextLine().trim();

                switch (choice) {
                    case "1":
                        showDashboard(baby, store);
                        break;
                    case "2":
                        addReminder(scanner, store);
                        break;
                    case "3":
                        manageReminders(scanner, store);
                        break;
                    case "4":
                        addGrowthEntry(scanner, store);
                        break;
                    case "5":
                        showGrowthEntries(store);
                        break;
                    case "6":
                        addNote(scanner, store);
                        break;
                    case "7":
                        showNotes(store);
                        break;
                    case "0":
                        running = false;
                        System.out.println("Տվյալները պահված են։ Հաջողություն։");
                        break;
                    default:
                        System.out.println("Այդպիսի ընտրություն չկա։ Փորձիր կրկին։");
                }
            }
        } catch (IOException exception) {
            System.out.println("Տվյալների ֆայլը կարդալու կամ պահելու սխալ՝ " + exception.getMessage());
        }
    }

    private static Baby getOrCreateBaby(Scanner scanner, BabyDataStore store) throws IOException {
        String name = store.get("baby.name");
        String date = store.get("baby.birthDate");

        if (name == null || date == null) {
            System.out.println("Baby Development Calendar");
            System.out.println("-------------------------");
            name = readNonEmpty(scanner, "Երեխայի անունը՝ ");
            LocalDate birthDate = readBirthDate(scanner);
            store.set("baby.name", name);
            store.set("baby.birthDate", birthDate.toString());
            store.save();
            return new Baby(name, birthDate);
        }

        System.out.println("Բարի գալուստ, " + name + "։");
        return new Baby(name, LocalDate.parse(date));
    }

    private static void printMenu() {
        System.out.println("\nԳործողություններ");
        System.out.println("1. Տարիք և այս ամսվա տեղեկություն");
        System.out.println("2. Ավելացնել հիշեցում");
        System.out.println("3. Դիտել կամ հեռացնել հիշեցումները");
        System.out.println("4. Գրանցել քաշն ու հասակը");
        System.out.println("5. Դիտել աճի գրառումները");
        System.out.println("6. Ավելացնել ծնողի նշում");
        System.out.println("7. Դիտել ծնողի նշումները");
        System.out.println("0. Ելք");
        System.out.print("Ընտրիր գործողությունը՝ ");
    }

    private static void showDashboard(Baby baby, BabyDataStore store) {
        LocalDate today = LocalDate.now();
        Period age = Period.between(baby.getBirthDate(), today);
        int ageInMonths = age.getYears() * 12 + age.getMonths();

        System.out.println("\n=== Օրացույց՝ " + baby.getName() + " ===");
        System.out.println("Ծննդյան ամսաթիվը՝ " + baby.getBirthDate());
        System.out.println("Տարիքը՝ " + formatAge(age));

        System.out.println("\nԿերակրում");
        printFeedingInfo(ageInMonths);

        System.out.println("\nՔուն");
        printSleepInfo(ageInMonths);

        System.out.println("\nԶարգացման տարիքային փուլ");
        printDevelopmentInfo(ageInMonths);

        System.out.println("\nԲժշկական և պատվաստման հիշեցումներ");
        System.out.println("Ծրագիրը չի որոշում պատվաստումների ժամանակացույցը։ Ավելացրու բժշկի կամ պատվաստման ժամադրությունը «Ավելացնել հիշեցում» բաժնում և ճշտիր այն մանկաբույժի հետ։");
        printNextReminder(store);
    }

    private static String formatAge(Period age) {
        StringBuilder result = new StringBuilder();
        if (age.getYears() > 0) {
            result.append(age.getYears()).append(" տարի ");
        }
        result.append(age.getMonths()).append(" ամիս ")
                .append(age.getDays()).append(" օր");
        return result.toString();
    }

    private static void printFeedingInfo(int ageInMonths) {
        if (ageInMonths < 6) {
            System.out.println("Այս տարիքում ընդհանուր ուղեցույցը կրծքի կաթն է կամ մանկական խառնուրդը։ Կերակրման հաճախականությունը անհատական է․ հետևիր երեխայի քաղցի և հագեցածության նշաններին ու բժշկի խորհրդին։");
            System.out.println("Լրացուցիչ սնունդը սովորաբար քննարկվում է մոտ 6 ամսականում՝ երեխայի պատրաստվածության նշանները հաշվի առնելով։");
        } else if (ageInMonths < 12) {
            System.out.println("Կրծքի կաթը կամ մանկական խառնուրդը շարունակում է կարևոր մնալ։ Լրացուցիչ սնունդը ներմուծվում է աստիճանաբար՝ հաշվի առնելով պատրաստվածությունը և մանկաբույժի խորհուրդը։");
        } else {
            System.out.println("Սննդակարգը կախված է երեխայի աճից, առողջությունից և ընտանեկան սննդից։ Անհատական սննդային հարցերը քննարկիր մանկաբույժի հետ։");
        }
        System.out.println("Հիշեցման ժամերը ծնողն է սահմանում․ ծրագիրը չի առաջարկում բժշկական սնուցման գրաֆիկ։");
    }

    private static void printSleepInfo(int ageInMonths) {
        if (ageInMonths < 4) {
            System.out.println("CDC-ի ընդհանուր ուղեցույցով 0–3 ամսական նորածինների քունը մոտ 14–17 ժամ է մեկ օրում։ Անհատական կարիքները տարբեր են։");
        } else if (ageInMonths < 12) {
            System.out.println("CDC-ի ընդհանուր ուղեցույցով 4–12 ամսականների քունը 12–16 ժամ է մեկ օրում՝ ներառյալ ցերեկային քունը։");
        } else if (ageInMonths < 36) {
            System.out.println("CDC-ի ընդհանուր ուղեցույցով 1–2 տարեկանների քունը 11–14 ժամ է մեկ օրում՝ ներառյալ ցերեկային քունը։");
        } else {
            System.out.println("Քնի պահանջը տարիքից և երեխայից է կախված․ անհատական խնդիրների դեպքում դիմիր բժշկին։");
        }
        System.out.println("Մինչև 1 տարեկանը քնեցնել մեջքի վրա՝ հարթ, ամուր մակերեսին, առանց բարձերի ու փափուկ իրերի։");
    }

    private static void printDevelopmentInfo(int ageInMonths) {
        int[] checkpoints = {2, 4, 6, 9, 12, 15, 18, 24};
        int applicable = -1;
        int next = -1;
        for (int checkpoint : checkpoints) {
            if (checkpoint <= ageInMonths) {
                applicable = checkpoint;
            } else if (next == -1) {
                next = checkpoint;
            }
        }

        if (applicable == -1) {
            applicable = 2;
            System.out.println("Մինչև 2 ամսական CDC-ի այս ցուցակում տարիքային ստուգաթերթ չկա։ Առաջին նշված ստուգաթերթը 2 ամսականինն է։");
        } else {
            System.out.println("CDC-ի " + applicable + " ամսականի ստուգաթերթի օրինակներ՝");
            for (String milestone : milestonesFor(applicable)) {
                System.out.println("• " + milestone);
            }
        }

        if (next != -1) {
            System.out.println("Հաջորդ տարիքային ստուգաթերթը՝ " + next + " ամսականում։");
        }
        System.out.println("Եթե տարիքը երկու ստուգաթերթերի միջև է, ցուցադրվում է նախորդ՝ ավելի փոքր տարիքային խումբը։ Վաղաժամ ծննդի դեպքում բժշկի հետ ճշտիր ճշգրտված տարիքը։");
        System.out.println("Սրանք զարգացման դիտարկման օրինակներ են, ոչ ախտորոշում։ Եթե մտահոգություն կա կամ երեխան կորցրել է նախկին հմտություն, խոսիր մանկաբույժի հետ։");
    }

    private static String[] milestonesFor(int months) {
        switch (months) {
            case 2:
                return new String[]{"նայել դեմքին և ժպտալ, երբ խոսում կամ ժպտում են իրեն", "արձագանքել բարձր ձայներին", "փորի վրա պառկած գլուխը բարձրացնել"};
            case 4:
                return new String[]{"ինքնուրույն ժպտալ՝ ուշադրություն գրավելու համար", "ձայներ արձակել և պատասխանել խոսակցությանը", "գլուխը կայուն պահել և ձեռքը տանել դեպի խաղալիքը"};
            case 6:
                return new String[]{"ճանաչել ծանոթ մարդկանց և ծիծաղել", "հերթով ձայներ արձակել և ճչալ", "փորից մեջքի վրա շրջվել ու ձեռք մեկնել խաղալիքին"};
            case 9:
                return new String[]{"արձագանքել իր անվանը", "բազմազան վանկեր արձակել", "առանց հենարանի նստել և առարկան մի ձեռքից մյուսը փոխանցել"};
            case 12:
                return new String[]{"խաղալ պարզ հերթափոխով խաղեր և ձեռքով հրաժեշտ տալ", "փորձել ծնողին դիմել հատուկ բառով", "կահույքից բռնած կանգնել կամ քայլել"};
            case 15:
                return new String[]{"փորձել ասել մեկ կամ երկու բառ՝ բացի «մամա»-ից կամ «պապա»-ից", "մատով ցույց տալ՝ ինչ-որ բան խնդրելու կամ օգնություն ստանալու համար", "ինքնուրույն մի քանի քայլ անել"};
            case 18:
                return new String[]{"ասել առնվազն երեք բառ՝ բացի «մամա»-ից կամ «պապա»-ից", "հետևել մեկ քայլից բաղկացած պարզ հրահանգի", "առանց բռնվելու քայլել"};
            case 24:
                return new String[]{"ասել առնվազն երկու բառ իրար հետևից", "ցույց տալ մարմնի մասեր՝ խնդրելիս", "վազել և գդալով ուտել"};
            default:
                return new String[0];
        }
    }

    private static void printNextReminder(BabyDataStore store) {
        List<Reminder> reminders = getReminders(store);
        LocalDateTime now = LocalDateTime.now();
        Reminder next = null;
        for (Reminder reminder : reminders) {
            if (!reminder.dateTime.isBefore(now)
                    && (next == null || reminder.dateTime.isBefore(next.dateTime))) {
                next = reminder;
            }
        }

        if (next == null) {
            System.out.println("Առաջիկա հիշեցում չկա։ Կարող ես ավելացնել հաջորդ կերակրման կամ բժշկի ժամադրության հիշեցում։");
            return;
        }

        long minutes = ChronoUnit.MINUTES.between(now, next.dateTime);
        System.out.println("Հաջորդը՝ " + next.type + " — " + next.title + " — "
                + next.dateTime.format(DATE_TIME_OUTPUT));
        if (minutes < 60) {
            System.out.println("Մնացել է մոտ " + minutes + " րոպե։");
        } else if (minutes < 1440) {
            System.out.println("Մնացել է մոտ " + (minutes / 60) + " ժամ։");
        } else {
            System.out.println("Մնացել է մոտ " + (minutes / 1440) + " օր։");
        }
    }

    private static void addReminder(Scanner scanner, BabyDataStore store) throws IOException {
        System.out.println("Հիշեցման տեսակը՝ 1) կերակրում  2) քուն  3) բժշկական/պատվաստում  4) այլ");
        String typeChoice = readChoice(scanner, "Ընտրիր՝ ", "1", "2", "3", "4");
        String type = typeFor(typeChoice);
        String title = readNonEmpty(scanner, "Նկարագրությունը՝ ");
        LocalDateTime dateTime = readFutureDateTime(scanner);

        Properties data = store.properties();
        int index = Integer.parseInt(data.getProperty("reminders.count", "0"));
        String prefix = "reminder." + index + ".";
        data.setProperty(prefix + "type", type);
        data.setProperty(prefix + "title", title);
        data.setProperty(prefix + "dateTime", dateTime.toString());
        data.setProperty("reminders.count", Integer.toString(index + 1));
        store.save();
        System.out.println("Հիշեցումը պահվեց։");
    }

    private static String typeFor(String choice) {
        switch (choice) {
            case "1": return "Կերակրում";
            case "2": return "Քուն";
            case "3": return "Բժշկական/պատվաստում";
            default: return "Այլ";
        }
    }

    private static void manageReminders(Scanner scanner, BabyDataStore store) throws IOException {
        List<Reminder> reminders = getReminders(store);
        if (reminders.isEmpty()) {
            System.out.println("Հիշեցումներ դեռ չկան։");
            return;
        }

        reminders.sort(Comparator.comparing(reminder -> reminder.dateTime));
        System.out.println("\nՀիշեցումներ");
        for (int i = 0; i < reminders.size(); i++) {
            Reminder reminder = reminders.get(i);
            System.out.println((i + 1) + ". " + reminder.dateTime.format(DATE_TIME_OUTPUT)
                    + " | " + reminder.type + " | " + reminder.title);
        }
        System.out.print("Հեռացնելու համար գրիր համար, կամ Enter՝ վերադառնալու համար՝ ");
        String input = scanner.nextLine().trim();
        if (input.isEmpty()) {
            return;
        }

        try {
            int selected = Integer.parseInt(input);
            if (selected < 1 || selected > reminders.size()) {
                System.out.println("Այդ համարով հիշեցում չկա։");
                return;
            }
            Reminder removed = reminders.remove(selected - 1);
            saveReminders(store, reminders);
            System.out.println("Հեռացվեց՝ " + removed.title);
        } catch (NumberFormatException exception) {
            System.out.println("Մուտքագրիր ցուցակի համարը կամ Enter։");
        }
    }

    private static List<Reminder> getReminders(BabyDataStore store) {
        Properties data = store.properties();
        int count = Integer.parseInt(data.getProperty("reminders.count", "0"));
        List<Reminder> reminders = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String prefix = "reminder." + i + ".";
            String dateTime = data.getProperty(prefix + "dateTime");
            if (dateTime != null) {
                reminders.add(new Reminder(
                        data.getProperty(prefix + "type", "Այլ"),
                        data.getProperty(prefix + "title", ""),
                        LocalDateTime.parse(dateTime)));
            }
        }
        return reminders;
    }

    private static void saveReminders(BabyDataStore store, List<Reminder> reminders) throws IOException {
        Properties data = store.properties();
        clearIndexedKeys(data, "reminder.");
        for (int i = 0; i < reminders.size(); i++) {
            Reminder reminder = reminders.get(i);
            String prefix = "reminder." + i + ".";
            data.setProperty(prefix + "type", reminder.type);
            data.setProperty(prefix + "title", reminder.title);
            data.setProperty(prefix + "dateTime", reminder.dateTime.toString());
        }
        data.setProperty("reminders.count", Integer.toString(reminders.size()));
        store.save();
    }

    private static void addGrowthEntry(Scanner scanner, BabyDataStore store) throws IOException {
        LocalDate date = readPastOrTodayDate(scanner, "Չափման ամսաթիվը (YYYY-MM-DD)՝ ");
        double weight = readPositiveNumber(scanner, "Քաշը կիլոգրամներով (օր.՝ 6.4)՝ ");
        double height = readPositiveNumber(scanner, "Հասակը սանտիմետրերով (օր.՝ 61.5)՝ ");

        Properties data = store.properties();
        int index = Integer.parseInt(data.getProperty("growth.count", "0"));
        String prefix = "growth." + index + ".";
        data.setProperty(prefix + "date", date.toString());
        data.setProperty(prefix + "weightKg", Double.toString(weight));
        data.setProperty(prefix + "heightCm", Double.toString(height));
        data.setProperty("growth.count", Integer.toString(index + 1));
        store.save();
        System.out.println("Աճի գրառումը պահվեց։");
    }

    private static void showGrowthEntries(BabyDataStore store) {
        Properties data = store.properties();
        int count = Integer.parseInt(data.getProperty("growth.count", "0"));
        if (count == 0) {
            System.out.println("Աճի գրառումներ դեռ չկան։");
            return;
        }

        System.out.println("\nԱճի գրառումներ");
        for (int i = 0; i < count; i++) {
            String prefix = "growth." + i + ".";
            System.out.println(data.getProperty(prefix + "date") + " — քաշ՝ "
                    + data.getProperty(prefix + "weightKg") + " կգ, հասակ՝ "
                    + data.getProperty(prefix + "heightCm") + " սմ");
        }
        System.out.println("Այս գրառումները չեն գնահատում աճի նորմաները։ Դրանք քննարկիր մանկաբույժի հետ։");
    }

    private static void addNote(Scanner scanner, BabyDataStore store) throws IOException {
        String text = readNonEmpty(scanner, "Նշումը՝ ");
        Properties data = store.properties();
        int index = Integer.parseInt(data.getProperty("notes.count", "0"));
        String prefix = "note." + index + ".";
        data.setProperty(prefix + "date", LocalDate.now().toString());
        data.setProperty(prefix + "text", text);
        data.setProperty("notes.count", Integer.toString(index + 1));
        store.save();
        System.out.println("Նշումը պահվեց։");
    }

    private static void showNotes(BabyDataStore store) {
        Properties data = store.properties();
        int count = Integer.parseInt(data.getProperty("notes.count", "0"));
        if (count == 0) {
            System.out.println("Նշումներ դեռ չկան։");
            return;
        }

        System.out.println("\nԾնողի նշումներ");
        for (int i = 0; i < count; i++) {
            String prefix = "note." + i + ".";
            System.out.println((i + 1) + ". " + data.getProperty(prefix + "date")
                    + " — " + data.getProperty(prefix + "text"));
        }
    }

    private static LocalDate readBirthDate(Scanner scanner) {
        while (true) {
            LocalDate date = readDate(scanner, "Ծննդյան ամսաթիվը (YYYY-MM-DD)՝ ");
            if (!date.isAfter(LocalDate.now())) {
                return date;
            }
            System.out.println("Ծննդյան ամսաթիվը չի կարող ապագայում լինել։");
        }
    }

    private static LocalDate readPastOrTodayDate(Scanner scanner, String prompt) {
        while (true) {
            LocalDate date = readDate(scanner, prompt);
            if (!date.isAfter(LocalDate.now())) {
                return date;
            }
            System.out.println("Ամսաթիվը չի կարող ապագայում լինել։");
        }
    }

    private static LocalDate readDate(Scanner scanner, String prompt) {
        while (true) {
            System.out.print(prompt);
            try {
                return LocalDate.parse(scanner.nextLine().trim());
            } catch (DateTimeParseException exception) {
                System.out.println("Ամսաթիվը գրիր YYYY-MM-DD ձևաչափով։ Օրինակ՝ 2026-09-07։");
            }
        }
    }

    private static LocalDateTime readFutureDateTime(Scanner scanner) {
        while (true) {
            System.out.print("Ամսաթիվ և ժամ (YYYY-MM-DD HH:mm)՝ ");
            try {
                LocalDateTime dateTime = LocalDateTime.parse(scanner.nextLine().trim(), DATE_TIME_INPUT);
                if (!dateTime.isBefore(LocalDateTime.now())) {
                    return dateTime;
                }
                System.out.println("Հիշեցման ժամը պետք է լինի ներկա կամ ապագա։");
            } catch (DateTimeParseException exception) {
                System.out.println("Մուտքագրիր ճիշտ ձևաչափով, օրինակ՝ 2026-10-01 09:30։");
            }
        }
    }

    private static double readPositiveNumber(Scanner scanner, String prompt) {
        while (true) {
            System.out.print(prompt);
            try {
                double value = Double.parseDouble(scanner.nextLine().trim().replace(',', '.'));
                if (Double.isFinite(value) && value > 0) {
                    return value;
                }
            } catch (NumberFormatException ignored) {
                // Ask again below.
            }
            System.out.println("Մուտքագրիր զրոյից մեծ թիվ։");
        }
    }

    private static String readNonEmpty(Scanner scanner, String prompt) {
        while (true) {
            System.out.print(prompt);
            String value = scanner.nextLine().trim();
            if (!value.isEmpty()) {
                return value;
            }
            System.out.println("Դաշտը դատարկ մի թող։");
        }
    }

    private static String readChoice(Scanner scanner, String prompt, String... allowed) {
        while (true) {
            System.out.print(prompt);
            String value = scanner.nextLine().trim();
            for (String option : allowed) {
                if (option.equals(value)) {
                    return value;
                }
            }
            System.out.println("Ընտրիր առաջարկված համարներից մեկը։");
        }
    }

    private static void clearIndexedKeys(Properties data, String prefix) {
        List<String> keys = new ArrayList<>();
        for (Object key : data.keySet()) {
            String text = key.toString();
            if (text.startsWith(prefix) && !text.equals(prefix + "count")) {
                keys.add(text);
            }
        }
        for (String key : keys) {
            data.remove(key);
        }
    }

    private static class Reminder {
        private final String type;
        private final String title;
        private final LocalDateTime dateTime;

        private Reminder(String type, String title, LocalDateTime dateTime) {
            this.type = type;
            this.title = title;
            this.dateTime = dateTime;
        }
    }
}
