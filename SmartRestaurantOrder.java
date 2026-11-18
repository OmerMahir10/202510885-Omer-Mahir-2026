import java.util.Scanner;

public class SmartRestaurantOrder {
    public static void main(String[] args) {
        
        try (Scanner input = new Scanner(System.in)) {
            
            final double DRINK_PRICE = 15.50;
            final double STUDENT_DISCOUNT = 0.10;
            final double SENIOR_DISCOUNT = 0.05;
            final double TIP_RATE = 0.10;
            final int SENIOR_AGE = 60;
            
            
            System.out.print("Customer name: ");
            String name = input.nextLine();
            
            System.out.print("Customer age: ");
            int age = input.nextInt();
            
            System.out.print("Meal price: $");
            double mealPrice = input.nextDouble();
            
            System.out.print("Number of drinks: ");
            int drinks = input.nextInt();
            
            System.out.print("Is the customer a student? (true/false): ");
            boolean isStudent = input.nextBoolean();
            
            System.out.print("Service rating (1-5): ");
            int rating = input.nextInt();
            
            if (rating < 1 || rating > 5) {
                System.out.println("Error: the service rating must be between 1 and 5.");
            } else {
                
                double drinksCost = drinks * DRINK_PRICE;
                double subtotal = mealPrice + drinksCost;
                
                
                boolean isSenior = age >= SENIOR_AGE;
                double discountRate;
                if (isStudent && isSenior) {
                    if (STUDENT_DISCOUNT > SENIOR_DISCOUNT) {
                        discountRate = STUDENT_DISCOUNT;
                    } else {
                        discountRate = SENIOR_DISCOUNT;
                    }
                } else if (isStudent) {
                    discountRate = STUDENT_DISCOUNT;
                } else if (isSenior) {
                    discountRate = SENIOR_DISCOUNT;
                } else {
                    discountRate = 0;
                }
                double discount = subtotal * discountRate;
                
                
                double tip = 0;
                if (rating == 4 || rating == 5) {
                    tip = (subtotal - discount) * TIP_RATE;
                }
                
                double finalBill = subtotal - discount + tip;
                int wholeAmount = (int) finalBill;
                
                
                System.out.println();
                System.out.println("SMART RESTAURANT");
                System.out.println("Customer: " + name);
                System.out.printf("Meal: $%.2f%n", mealPrice);
                System.out.printf("Drinks: $%.2f%n", drinksCost);
                System.out.printf("Discount: $%.2f%n", discount);
                System.out.printf("Tip: $%.2f%n", tip);
                System.out.printf("Final bill: $%.2f%n", finalBill);
                System.out.println("Whole amount: $" + wholeAmount);
            }
        }
    }
}
