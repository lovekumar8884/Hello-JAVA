import java.util.*;
public class FNRC {
    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        System.out.println("Enter the string:");
        String s = sc.nextLine();
        char result = findFirstNonRepeatingCharacter(s);
        if (result != 0) {
            System.out.println("The first non-repeating character is: " + result);
        } else {
            System.out.println("There are no non-repeating characters.");
        }
    }

    public static char findFirstNonRepeatingCharacter(String s) {
        Map<Character, Integer> countMap = new LinkedHashMap<>();
        for (char c : s.toCharArray()) {
            countMap.put(c, countMap.getOrDefault(c, 0) + 1);
        }
        for (Map.Entry<Character, Integer> entry : countMap.entrySet()) {
            if (entry.getValue() == 1) {
                return entry.getKey();
            }
        }
        return 0; // no non-repeating character
    }
}