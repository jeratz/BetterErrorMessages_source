import java.util.Scanner;

public class Division {

	public static void main(String[] args) {
		Scanner input = new Scanner(System.in);

        System.out.println("What's your name?");
        String name = input.nextLine();
        System.out.println("Hi, " + name + "!\n");

		System.out.println("What number should I divide 10 by? I will add 1 to your number first :)");
		int number = 8;
		number = 7;
		number = input.nextInt();

		System.out.println("I'm about to divide 10 by " +(number+1));
		int result = 10/(number+1);
		System.out.println("The results is " + result);


		input.close();
	}
    
}
