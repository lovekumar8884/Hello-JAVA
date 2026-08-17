/*
 * ============================================================================
 * INTERVIEW QUESTION:
 *   "Implement Merge Sort. Explain how it works, its time/space complexity,
 *    and when you'd pick it over Quick Sort."
 *
 * IDEA (Divide and Conquer):
 *   1. DIVIDE  — split the array into two halves.
 *   2. CONQUER — recursively sort each half.
 *   3. COMBINE — merge the two sorted halves into one sorted array.
 *
 *   Recursion tree has log2(n) levels; each level does O(n) merge work
 *   → O(n log n) total.
 *
 * COMPLEXITY:
 *   Time:  Best = Average = Worst = O(n log n)   ← guaranteed, unlike QuickSort
 *   Space: O(n)   ← needs an auxiliary buffer for the merge step
 *   Stable: YES   ← equal elements keep their original relative order
 *   In-place: NO  ← the classic version uses an aux array
 *
 * WHY MERGE SORT VS QUICK SORT?
 *   • Merge sort has GUARANTEED O(n log n) — no worst-case O(n^2) like Quick.
 *   • Merge sort is STABLE — QuickSort is not.
 *   • Merge sort is the go-to for LINKED LISTS (no random access → no QuickSort).
 *   • Merge sort is used for EXTERNAL SORTING (data > RAM) — sort chunks in
 *     memory, then k-way merge from disk.
 *   • QuickSort wins in-place on arrays: better cache locality + O(log n) space.
 *
 * JAVA CONNECTION:
 *   • Arrays.sort(Object[]) and Collections.sort(List) use TIMSORT — a hybrid of
 *     MERGE SORT and INSERTION SORT — precisely because merge sort is stable
 *     and O(n log n) worst-case.
 *   • Arrays.sort(int[]) uses Dual-Pivot QuickSort (primitives don't need
 *     stability, and QuickSort is faster in practice with primitives).
 *
 * FOLLOW-UPS THE INTERVIEWER USUALLY ASKS:
 *   1. Iterative (bottom-up) version? → merge pairs of size 1, then 2, then 4...
 *      No recursion → no stack overflow for huge arrays.
 *   2. Sort a linked list in O(n log n) with O(1) extra space?
 *      → Merge sort using slow/fast pointer to find the middle.
 *   3. How would you sort 100 GB of data with 1 GB RAM?
 *      → External merge sort: sort chunks that fit in RAM, then k-way merge.
 *   4. Count inversions in an array? → modified merge sort counts pairs
 *      (i < j, a[i] > a[j]) during merge → O(n log n).
 *   5. Parallelize it? → ForkJoin: RecursiveAction; split until threshold,
 *      then sequential sort + merge on join.
 *
 * KEY POINTS TO REMEMBER:
 *   ⭐ Guaranteed O(n log n) — merge sort's headline feature.
 *   ⭐ STABLE and predictable — that's why it backs Timsort in Java/Python.
 *   ⭐ O(n) extra memory — the trade-off vs QuickSort.
 *   ⭐ Merge step is the heart: two sorted arrays → one sorted array in O(n).
 *   ⭐ Use `mid = left + (right - left) / 2` to avoid int overflow on huge indices.
 *   ⭐ Recurse on `[left, mid]` and `[mid+1, right]` (inclusive), or on
 *      `[left, mid)` and `[mid, right)` (exclusive) — pick ONE convention
 *      and stay consistent, otherwise off-by-one bugs.
 * ============================================================================
 */
import java.util.Arrays;
import java.util.Scanner;

public class Coding_MergeSort {

    public static void main(String[] args) {
        try (Scanner sc = new Scanner(System.in)) {
            System.out.println("Enter numbers separated by spaces:");
            String line = sc.nextLine().trim();
            if (line.isEmpty()) { System.out.println("[]"); return; }

            int[] arr = Arrays.stream(line.split("\\s+"))
                              .mapToInt(Integer::parseInt)
                              .toArray();

            mergeSort(arr, 0, arr.length - 1);
            System.out.println("Sorted: " + Arrays.toString(arr));
        }
    }

    /**
     * Recursive top-down merge sort. Sorts a[left..right] INCLUSIVE in place
     * (uses an auxiliary buffer internally).
     * O(n log n) time, O(n) auxiliary space.
     */
    public static void mergeSort(int[] a, int left, int right) {
        if (left >= right) return;                    // base case: 0 or 1 element

        int mid = left + (right - left) / 2;          // overflow-safe midpoint

        mergeSort(a, left, mid);                      // sort left half
        mergeSort(a, mid + 1, right);                 // sort right half
        merge(a, left, mid, right);                   // combine the two halves
    }

    /**
     * Merges a[left..mid] and a[mid+1..right], both already sorted,
     * into a single sorted run a[left..right]. O(n) time.
     */
    private static void merge(int[] a, int left, int mid, int right) {
        int n = right - left + 1;
        int[] tmp = new int[n];

        int i = left, j = mid + 1, k = 0;

        while (i <= mid && j <= right) {
            // `<=` (not `<`) keeps equal elements in left-first order → STABLE
            if (a[i] <= a[j]) tmp[k++] = a[i++];
            else              tmp[k++] = a[j++];
        }
        while (i <= mid)   tmp[k++] = a[i++];         // drain leftovers (left)
        while (j <= right) tmp[k++] = a[j++];         // drain leftovers (right)

        System.arraycopy(tmp, 0, a, left, n);         // copy back into original
    }

    // ------------------------------------------------------------------
    // BONUS: iterative bottom-up merge sort — no recursion, same O(n log n).
    // Useful when the interviewer asks about huge arrays / stack-overflow risk.
    // ------------------------------------------------------------------
    public static void mergeSortIterative(int[] a) {
        int n = a.length;
        for (int width = 1; width < n; width *= 2) {                // 1, 2, 4, 8...
            for (int left = 0; left < n - width; left += 2 * width) {
                int mid   = left + width - 1;
                int right = Math.min(left + 2 * width - 1, n - 1);
                merge(a, left, mid, right);
            }
        }
    }
}
