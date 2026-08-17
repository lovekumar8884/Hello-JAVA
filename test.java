import java.util.Map;
import java.util.Scanner;
import java.util.LinkedHashMap;

public class test {
    public static void main(String[] args){
        Scanner sc = new Scanner(System.in);
        String s = sc.nextLine();
        System.out.println(firstUniqChar(s));
    }
    public static int firstUniqChar(String s){
        Map<Character, Integer> countMap = new LinkedHashMap<>();
        for (char c : s.toCharArray())
            countMap.put(c, countMap.getOrDefault(c, 0) + 1);
        for (int i = 0; i < s.length(); i++)
            if (countMap.get(s.charAt(i)) == 1)
                return i;
        return -1;
    }
}
