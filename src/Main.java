import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

class VehicleUnavailableException extends Exception {
    public VehicleUnavailableException(String message) {
        super(message);
    }
}

abstract class Vehicle {
    private final String id;
    private final String brand;
    private final double basePricePerDay;
    protected boolean isAvailable;

    public Vehicle(String id, String brand, double basePricePerDay) {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("ID не может быть пустым.");
        }
        if (basePricePerDay <= 0) {
            throw new IllegalArgumentException("Базовая цена должна быть положительной.");
        }
        this.id = id;
        this.brand = brand;
        this.basePricePerDay = basePricePerDay;
        this.isAvailable = true;
    }

    public String getId() { return id; }
    public String getBrand() { return brand; }
    public double getBasePricePerDay() { return basePricePerDay; }
    public boolean isAvailable() { return isAvailable; }
    public void setAvailable(boolean available) { isAvailable = available; }

    public abstract double calculateRentCost(int days);
    public abstract String getVehicleType();

    @Override
    public String toString() {
        return String.format("[%s] %s %s | Цена/сутки: %.2f | Статус: %s",
                id, getVehicleType(), brand, basePricePerDay, isAvailable ? "Доступен" : "В аренде");
    }
}

class Car extends Vehicle {
    private final int doors;

    public Car(String id, String brand, double basePricePerDay, int doors) {
        super(id, brand, basePricePerDay);
        this.doors = doors;
    }

    @Override
    public double calculateRentCost(int days) {
        return getBasePricePerDay() * days;
    }

    @Override
    public String getVehicleType() { return "Автомобиль"; }
}

class Motorcycle extends Vehicle {
    private final int engineVolume;

    public Motorcycle(String id, String brand, double basePricePerDay, int engineVolume) {
        super(id, brand, basePricePerDay);
        this.engineVolume = engineVolume;
    }

    @Override
    public double calculateRentCost(int days) {
        return getBasePricePerDay() * days * 0.8; // Скидка 20%
    }

    @Override
    public String getVehicleType() { return "Мотоцикл"; }
}

interface RentalManager {
    void addVehicle(Vehicle v);
    void removeVehicle(String id) throws IllegalArgumentException;
    void rentVehicle(String id, int days) throws VehicleUnavailableException, IllegalArgumentException;
    void returnVehicle(String id) throws IllegalArgumentException;
}

class GenericRepository<T> {
    private final List<T> items = new ArrayList<>();

    public void add(T item) { items.add(item); }
    public List<T> getAll() { return new ArrayList<>(items); }
    public void removeIf(java.util.function.Predicate<T> condition) { items.removeIf(condition); }
}

class RentalService implements RentalManager {
    private final GenericRepository<Vehicle> repository = new GenericRepository<>();

    @Override
    public void addVehicle(Vehicle v) {
        repository.add(v);
    }

    @Override
    public void removeVehicle(String id) {
        boolean removed = repository.getAll().removeIf(v -> v.getId().equals(id));
        if (!removed) {
            throw new IllegalArgumentException("Транспорт с ID " + id + " не найден.");
        }
        repository.removeIf(v -> v.getId().equals(id));
    }

    @Override
    public void rentVehicle(String id, int days) throws VehicleUnavailableException {
        Vehicle v = findVehicle(id);
        if (!v.isAvailable()) {
            throw new VehicleUnavailableException("Транспорт " + id + " уже находится в аренде.");
        }
        if (days <= 0) {
            throw new IllegalArgumentException("Количество дней должно быть больше нуля.");
        }
        v.setAvailable(false);
    }

    @Override
    public void returnVehicle(String id) {
        Vehicle v = findVehicle(id);
        if (v.isAvailable()) {
            throw new IllegalArgumentException("Транспорт " + id + " и так свободен.");
        }
        v.setAvailable(true);
    }

    public Vehicle findVehicle(String id) {
        for (Vehicle v : repository.getAll()) {
            if (v.getId().equals(id)) {
                return v;
            }
        }
        throw new IllegalArgumentException("Транспорт с ID " + id + " не найден.");
    }

    public List<Vehicle> getAvailableVehicles() {
        List<Vehicle> result = new ArrayList<>();
        for (Vehicle v : repository.getAll()) {
            if (v.isAvailable()) result.add(v);
        }
        return result;
    }

    public List<Vehicle> getAllVehicles() {
        return repository.getAll();
    }

    public void saveToFile(String filename) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(filename);
             OutputStreamWriter osw = new OutputStreamWriter(fos, StandardCharsets.UTF_8);
             BufferedWriter writer = new BufferedWriter(osw)) {
            for (Vehicle v : repository.getAll()) {
                String type = (v instanceof Car) ? "CAR" : "MOTO";
                writer.write(type + ";" + v.getId() + ";" + v.getBrand() + ";" + v.getBasePricePerDay() + ";" + v.isAvailable());
                writer.newLine();
            }
        }
    }

    public void loadFromFile(String filename) throws IOException {
        File file = new File(filename);
        if (!file.exists()) return;

        try (FileInputStream fis = new FileInputStream(file);
             InputStreamReader isr = new InputStreamReader(fis, StandardCharsets.UTF_8);
             BufferedReader reader = new BufferedReader(isr)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                String[] parts = line.split(";");
                if (parts.length == 5) {
                    String type = parts[0];
                    String id = parts[1];
                    String brand = parts[2];
                    double price = Double.parseDouble(parts[3]);
                    boolean available = Boolean.parseBoolean(parts[4]);

                    Vehicle v;
                    if (type.equals("CAR")) {
                        v = new Car(id, brand, price, 4);
                    } else {
                        v = new Motorcycle(id, brand, price, 600);
                    }
                    v.setAvailable(available);
                    repository.add(v);
                }
            }
        } catch (NumberFormatException e) {
            throw new IOException("Ошибка формата данных в файле: " + e.getMessage());
        }
    }
}

public class Main {
    private static final String FILE_NAME = "rental_data.txt";
    private static final Scanner scanner = new Scanner(System.in);

    public static void main(String[] args) {
        RentalService service = new RentalService();

        try {
            service.loadFromFile(FILE_NAME);
            System.out.println("Данные успешно загружены.");
        } catch (IOException e) {
            System.out.println("Ошибка загрузки данных: " + e.getMessage());
        }

        if (service.getAllVehicles().isEmpty()) {
            System.out.println("База пуста. Заполнение тестовыми данными...");
            service.addVehicle(new Car("C1", "Toyota", 5000, 4));
            service.addVehicle(new Car("C2", "BMW", 8000, 2));
            service.addVehicle(new Motorcycle("M1", "Yamaha", 3000, 600));
            service.addVehicle(new Motorcycle("M2", "Honda", 3500, 750));
        }

        boolean running = true;
        while (running) {
            System.out.println("\n--- МЕНЮ ---");
            System.out.println("1. Добавить транспорт");
            System.out.println("2. Показать весь транспорт");
            System.out.println("3. Показать доступный транспорт");
            System.out.println("4. Оформить аренду");
            System.out.println("5. Вернуть транспорт");
            System.out.println("6. Удалить транспорт");
            System.out.println("0. Выход");
            System.out.print("Выбор: ");

            String choice = scanner.nextLine().trim();
            try {
                switch (choice) {
                    case "1":
                        addNewVehicle(service);
                        break;
                    case "2":
                        printList(service.getAllVehicles());
                        break;
                    case "3":
                        printList(service.getAvailableVehicles());
                        break;
                    case "4":
                        rentVehicle(service);
                        break;
                    case "5":
                        returnVehicle(service);
                        break;
                    case "6":
                        deleteVehicle(service);
                        break;
                    case "0":
                        running = false;
                        saveData(service);
                        break;
                    default:
                        System.out.println("Неверный пункт меню.");
                }
            } catch (Exception e) {
                System.out.println("Ошибка: " + e.getMessage());
            }
        }
        scanner.close();
    }

    private static void addNewVehicle(RentalService service) {
        System.out.print("Введите тип (CAR/MOTO): ");
        String type = scanner.nextLine().trim().toUpperCase();
        System.out.print("Введите ID: ");
        String id = scanner.nextLine().trim();
        System.out.print("Введите марку: ");
        String brand = scanner.nextLine().trim();
        System.out.print("Введите цену за сутки: ");
        double price = Double.parseDouble(scanner.nextLine().trim().replace(',', '.'));

        if (type.equals("CAR")) {
            service.addVehicle(new Car(id, brand, price, 4));
        } else if (type.equals("MOTO")) {
            service.addVehicle(new Motorcycle(id, brand, price, 600));
        } else {
            System.out.println("Неизвестный тип.");
        }
        System.out.println("Добавлено.");
    }

    private static void rentVehicle(RentalService service) throws Exception {
        System.out.print("Введите ID транспорта: ");
        String id = scanner.nextLine().trim();
        System.out.print("Введите количество дней: ");
        int days = Integer.parseInt(scanner.nextLine().trim());
        service.rentVehicle(id, days);
        Vehicle v = service.findVehicle(id);
        System.out.printf("Аренда оформлена. Стоимость: %.2f руб.%n", v.calculateRentCost(days));
    }

    private static void returnVehicle(RentalService service) throws Exception {
        System.out.print("Введите ID транспорта: ");
        String id = scanner.nextLine().trim();
        service.returnVehicle(id);
        System.out.println("Транспорт возвращён.");
    }

    private static void deleteVehicle(RentalService service) throws Exception {
        System.out.print("Введите ID транспорта: ");
        String id = scanner.nextLine().trim();
        service.removeVehicle(id);
        System.out.println("Удалено.");
    }

    private static void saveData(RentalService service) {
        try {
            service.saveToFile(FILE_NAME);
            System.out.println("Данные сохранены в " + FILE_NAME);
        } catch (IOException e) {
            System.out.println("Ошибка сохранения: " + e.getMessage());
        }
    }

    private static void printList(List<Vehicle> list) {
        if (list.isEmpty()) {
            System.out.println("Список пуст.");
            return;
        }
        for (Vehicle v : list) {
            System.out.println(v);
        }
    }
}