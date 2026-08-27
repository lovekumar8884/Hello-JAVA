/*
 * ============================================================================
 * INTERVIEW QUESTION:
 *   "Given a string, find the FIRST non-repeating character in it.
 *    e.g. 'swiss' -> 'w', 'aabbcc' -> none."
 *
 * WHY LinkedHashMap?
 *   Ordinary HashMap does NOT preserve insertion order, so we couldn't tell
 *   which unique character came first. LinkedHashMap keeps insertion order.
 *
 * COMPLEXITY:  Time O(n)  |  Space O(k) where k = distinct chars.
 *
 * FOLLOW-UPS THE INTERVIEWER USUALLY ASKS:
 *   1. Can you do it without LinkedHashMap?
 *      -> Yes: use int[128] frequency array + a second pass over the string
 *         (see Coding_FirstUniqueCharIndex.java).
 *   2. What if input is a STREAM (chars arrive one-by-one, must answer any time)?
 *      -> Maintain a LinkedHashMap<Char, Node> + a doubly-linked list of
 *         'currently-unique' chars. On duplicate arrival, remove from list.
 *         Head of list is always the first non-repeating char. O(1) per event.
 *   3. Unicode / emoji support?
 *      -> Iterate code points (s.codePoints()) instead of chars,
 *         and switch key type to Integer.
 *   4. Case sensitivity?
 *      -> Confirm requirement; if case-insensitive, normalise with
 *         Character.toLowerCase(c).
 *
 * ============================================================================
 * HOW MERGE() WORKS:
 *
 *   countMap.merge(c, 1, Integer::sum);
 *
 *   Meaning:
 *
 *   1. If the key does NOT exist:
 *         insert the key with value 1.
 *
 *   2. If the key ALREADY exists:
 *         combine old value + new value using Integer::sum.
 *
 *   In simple if/else form:
 *
 *       if (countMap.containsKey(c)) {
 *           countMap.put(c, countMap.get(c) + 1);
 *       } else {
 *           countMap.put(c, 1);
 *       }
 *
 *   So merge() is very useful for frequency counting.
 *
 * KEY POINTS TO REMEMBER:
 *   ⭐ First non-repeating != first char with count 1 in a HashMap.
 *     Order matters → use LinkedHashMap or a second pass over the string.
 *   ⭐ getOrDefault is the idiomatic frequency-count pattern.
 *   ⭐ Return sentinel: '\0' (0) means "not found" — not the same as space (' ').
 * ============================================================================
 */
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Scanner;

public class Coding_FirstNonRepeatingCharacter {

    public static void main(String[] args) {
        try (Scanner sc = new Scanner(System.in)) {
            System.out.println("Enter the string:");
            String s = sc.nextLine();
            char result = findFirstNonRepeatingCharacter(s);
            if (result != 0) {
                System.out.println("The first non-repeating character is: " + result);
            } else {
                System.out.println("There are no non-repeating characters.");
            }
        }
    }

    /**
     * Returns the first non-repeating character in {@code s}, or '\0' if none.
     * O(n) time, O(k) space where k is number of distinct characters.
     */
    public static char findFirstNonRepeatingCharacter(String s) {
        if (s == null || s.isEmpty()) return 0;

        // Insertion order matters → LinkedHashMap, NOT HashMap.
        Map<Character, Integer> countMap = new LinkedHashMap<>();
        for (char c : s.toCharArray()) {
            countMap.merge(c, 1, Integer::sum);
        }
        for (Map.Entry<Character, Integer> entry : countMap.entrySet()) {
            if (entry.getValue() == 1) return entry.getKey();
        }
        return 0;
    }




















}
