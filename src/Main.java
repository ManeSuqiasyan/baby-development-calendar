import java.time.LocalDate;
import java.time.Period;

public class Main {
    public static void main(String[] args) {
        Baby baby = new Baby();
        baby.name = "Արամ";
        baby.birthDate = "2026-09-07";

        LocalDate birthDate = LocalDate.parse(baby.birthDate);
        Period age = Period.between(birthDate, LocalDate.now());

        System.out.println("Baby Development Calendar");
        System.out.println("-------------------------");
        System.out.println("Երեխայի անունը՝ " + baby.name);
        System.out.println("Ծննդյան ամսաթիվը՝ " + baby.birthDate);
        System.out.println("Տարիքը՝ " + age.getYears() + " տարի, "
                + age.getMonths() + " ամիս, " + age.getDays() + " օր");
    }
}
