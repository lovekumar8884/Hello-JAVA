/*
 * ============================================================================
 * INTERVIEW QUESTION (LeetCode 387 — "First Unique Character in a String"):
 *   "Given a string s, find the first non-repeating character in it and
 *    return its INDEX. If it does not exist, return -1."
 *
 * DIFFERENCE FROM Coding_FirstNonRepeatingCharacter.java:
 *   That one returns the CHARACTER. This one returns the INDEX.
 *   → Because we need the index, the second loop iterates over the STRING
 *     (not the map), so the index is available directly.
 *
 * OPTIMAL SOLUTION — two passes, O(n) time, O(1) space (fixed alphabet).
 *
 * FOLLOW-UPS TO REHEARSE:
 *   • Why int[128] instead of HashMap? → constant-factor speed, no boxing/hashing.
 *   • What if only lowercase a–z? → int[26] with c - 'a'.
 *   • Can this be done in one pass? → No, you must know future duplicates first.
 *   • Streaming input? → See notes in Coding_FirstNonRepeatingCharacter.java
 *     (LinkedHashMap + doubly-linked list → O(1) per event).
 *
 * KEY POINTS TO REMEMBER:
 *   ⭐ "First unique" problems are ALWAYS two-pass unless input is streaming.
 *   ⭐ Frequency array is faster than HashMap when the alphabet is bounded.
 * ============================================================================
 */
import java.util.Scanner;

public class Coding_FirstUniqueCharIndex {

    public static void main(String[] args) {
        try (Scanner sc = new Scanner(System.in)) {
            System.out.println("Enter the string:");
            String s = sc.nextLine();
            System.out.println("Index of first unique char: " + firstUniqCharIndex(s));
        }
    }

    /** O(n) time, O(1) space (128-entry ASCII bucket). */
    public static int firstUniqCharIndex(String s) {
        if (s == null || s.isEmpty()) return -1;

        int[] count = new int[128]; // switch to HashMap for full Unicode
        for (int i = 0; i < s.length(); i++) count[s.charAt(i)]++;
        for (int i = 0; i < s.length(); i++) {
            if (count[s.charAt(i)] == 1) return i;
        }
        return -1;
    }
/*
 * public int firstUniqChar(String s) {
 *     if(s==null || s.isEmpty()) return -1;
 *     Map<Character, Integer> countMap = new LinkedHashMap<>();
 *     for(char c : s.toCharArray()) countMap.merge(c, 1, Integer::sum);
 *     for(int i = 0; i < s.length(); i++)
 *     {
 *         if(countMap.get(s.charAt(i))==1)
 *             return i;
 *
 *     }
 *     return -1;
 *
 * }
 */














}
