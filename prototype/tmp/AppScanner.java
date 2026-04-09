import java.util.Scanner;

public class AppScanner {
    public static void main(String[] args) {

        Scanner input = new Scanner(System.in);
        //input.hasNext();
        //input.close();
        System.out.println("Hello!");
        System.out.println("Please provide next input: ");
        //input.close();
        int divisor = input.nextInt();
        System.out.println("First divisor is " + divisor);

        System.out.println("Please provide next input: ");
        divisor = input.nextInt();
        System.out.println("Second divisor is " + divisor);

        System.out.println("Please provide next input: ");
        divisor = input.nextInt();
        System.out.println("Third divisor is " + divisor);

        System.out.println("Time to calculate!");

        int y = 2;
        y = 0;
        int x = 10/divisor;

        System.out.println("x is " + x);
    }
}
