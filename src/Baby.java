import java.time.LocalDate;

public class Baby {
    private final String name;
    private final LocalDate birthDate;

    public Baby(String name, LocalDate birthDate) {
        this.name = name;
        this.birthDate = birthDate;
    }

    public String getName() {
        return name;
    }

    public LocalDate getBirthDate() {
        return birthDate;
    }
}
