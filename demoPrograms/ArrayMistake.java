import java.util.Scanner;

public class ArrayMistake {

    public static void main(String[] args) {
        System.out.println("This program will cause an error");
        int index = 0;
        willBreak(index);

    }


    public static void willBreak(int index){
        index++;
        index++;
        double[] values = new double[2];
        System.out.println("About to access an index");
        values[0] = 5;
        double num = values[index]; 
        System.out.println("Found number is " + num);

    }
    
}
