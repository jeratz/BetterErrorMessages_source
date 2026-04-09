public class App {
    public static void main(String[] args) {
        int x = 1;
        System.out.println("This program will casue an error");
        returning(1);
        System.out.println("Im between functions");
        fun2();
        fun2();
        int y = 0;
        x = 10/y;
        y++;
        x--;

    }

    public static int returning(int t){
        int y = 3;
        int a = 1;
        int b = 0;
        int mistake = a/(t-1);
        y = y/(t*a);
        return y/t;
    }

    public static void fun2(){
        int some = 3;
    }
}
