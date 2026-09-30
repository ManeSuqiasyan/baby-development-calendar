import java.time.LocalDate;
import java.time.Period;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Scanner;

public class Main {
    private static final int CALENDAR_MONTHS = 24;
    private static final int UPCOMING_DATES_TO_SHOW = 3;

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        LocalDate today = LocalDate.now();

        System.out.println("Baby Development Calendar");
        System.out.println("-------------------------");

        System.out.print("Երեխայի անունը՝ ");
        String name = scanner.nextLine().trim();
        while (name.isEmpty()) {
            System.out.print("Անունը դատարկ է։ Նորից մուտքագրիր՝ ");
            name = scanner.nextLine().trim();
        }

        LocalDate birthDate = readBirthDate(scanner, today);
        Baby baby = new Baby(name, birthDate);
        Period age = Period.between(baby.getBirthDate(), today);

        System.out.println();
        System.out.println("Երեխայի անունը՝ " + baby.getName());
        System.out.println("Ծննդյան ամսաթիվը՝ " + baby.getBirthDate());
        System.out.println("Տարիքը՝ " + age.getYears() + " տարի, "
                + age.getMonths() + " ամիս, " + age.getDays() + " օր");

        printUpcomingMonthlyDates(baby, today, age);
        scanner.close();
    }

    private static LocalDate readBirthDate(Scanner scanner, LocalDate today) {
        while (true) {
            System.out.print("Ծննդյան ամսաթիվը (YYYY-MM-DD)՝ ");
            String input = scanner.nextLine().trim();

            try {
                LocalDate birthDate = LocalDate.parse(input);
                if (birthDate.isAfter(today)) {
                    System.out.println("Ծննդյան ամսաթիվը չի կարող ապագայում լինել։");
                    continue;
                }
                return birthDate;
            } catch (DateTimeParseException exception) {
                System.out.println("Ամսաթիվը ճիշտ ձևաչափով չէ։ Օրինակ՝ 2026-09-07։");
            }
        }
    }

    private static void printUpcomingMonthlyDates(Baby baby, LocalDate today, Period age) {
        int completedMonths = age.getYears() * 12 + age.getMonths();
        int firstMonth = Math.max(1, completedMonths + 1);
        int shown = 0;

        System.out.println();
        System.out.println("Առաջիկա ամսական օրերը");

        for (int month = firstMonth; month <= CALENDAR_MONTHS
                && shown < UPCOMING_DATES_TO_SHOW; month++) {
            LocalDate monthlyDate = baby.getBirthDate().plusMonths(month);
            if (monthlyDate.isBefore(today)) {
                continue;
            }

            long daysUntil = ChronoUnit.DAYS.between(today, monthlyDate);
            System.out.println(month + " ամսական՝ " + monthlyDate + " (մնացել է "
                    + daysUntil + " օր)");
            shown++;
        }

        if (shown == 0) {
            System.out.println("Մինչև 24 ամսական օրացույցի նշումներ այլևս չկան։");
        }
    }
}
