/*
 * ============================================================================
 * INTERVIEW QUESTION (LeetCode 73 — "Set Matrix Zeroes"):
 *   "Given an m x n matrix, if an element is 0 set its ENTIRE row and column
 *    to 0. Do it in place."
 *
 * THE TRAP:
 *   You cannot zero rows/columns as you scan — you would propagate 0s that
 *   were newly written and end up zeroing the whole matrix.
 *   So you must first RECORD which rows and columns need to be zeroed,
 *   then apply the change in a second pass.
 *
 * THREE STANDARD APPROACHES (interviewer walks you up the ladder):
 *
 *   1) BRUTE FORCE — O((m*n) * (m+n)) time, O(1) space.
 *      For every 0 found, walk its row and column marking them with a sentinel
 *      (e.g., Integer.MIN_VALUE). Second pass converts sentinels to 0.
 *      Only works if the sentinel isn't a valid input value.
 *
 *   2) EXTRA SPACE  — O(m*n) time, O(m + n) space.
 *      Two boolean arrays: rowZero[m], colZero[n]. First pass fills them.
 *      Second pass zeros a[i][j] if rowZero[i] || colZero[j].
 *      Clean, easy to explain — good "warm-up" answer.
 *
 *   3) OPTIMAL      — O(m*n) time, O(1) EXTRA space.  ⭐ Target answer.
 *      Reuse the FIRST ROW and FIRST COLUMN of the matrix itself as the
 *      row/column markers. But the top-left cell a[0][0] would be shared by
 *      both markers → use ONE separate boolean for the first column
 *      (or first row) to break the tie.
 *
 * COMPLEXITY (optimal):
 *   Time  : O(m*n)   — 4 passes over the matrix, each O(m*n)
 *   Space : O(1)     — no extra arrays; only a single boolean
 *
 * FOLLOW-UPS THE INTERVIEWER USUALLY ASKS:
 *   • Why can't you zero as you go?  → chain-reaction wipes the whole matrix.
 *   • Can you do it with O(1) extra space?  → yes — the optimal method below.
 *   • Why do you need the extra boolean for column 0?
 *     → because a[0][0] doubles as marker for row 0 AND column 0; one boolean
 *       untangles them.
 *   • Would your solution work if the matrix contained MIN_VALUE?
 *     → the brute-force sentinel version wouldn't; the optimal one does.
 *   • Space O(1) for real? → we mutate the input, so we don't count it as extra.
 *
 * KEY POINTS TO REMEMBER:
 *   ⭐ Two-pass algorithm: MARK first, then WIPE.
 *   ⭐ In optimal version: first row + first column become the "marker arrays".
 *   ⭐ You need ONE separate flag for either the first row OR the first column
 *     (the shared a[0][0] cell is the reason).
 *   ⭐ Apply the wipe to the inner submatrix first, then handle row 0 / col 0
 *     LAST — otherwise you'd overwrite the markers before using them.
 * ============================================================================
 */
import java.util.Arrays;

public class Coding_SetMatrixZeroes {

    public static void main(String[] args) {
        int[][] m = {
            {1, 1, 1},
            {1, 0, 1},
            {1, 1, 1}
        };
        setZeroesOptimal(m);
        for (int[] row : m) System.out.println(Arrays.toString(row));
        // Expected:
        // [1, 0, 1]
        // [0, 0, 0]
        // [1, 0, 1]
    }

    /**
     * OPTIMAL — O(m*n) time, O(1) extra space.
     * Uses row 0 and column 0 of the matrix itself as markers.
     */
    public static void setZeroesOptimal(int[][] a) {
        if (a == null || a.length == 0) return;
        int m = a.length, n = a[0].length;

        // One separate flag is needed because a[0][0] must serve two roles.
        boolean firstColHasZero = false;

        // PASS 1 — mark: use row 0 and col 0 as flags for the rest of the matrix
        for (int i = 0; i < m; i++) {
            if (a[i][0] == 0) firstColHasZero = true;   // remember col 0 separately
            for (int j = 1; j < n; j++) {
                if (a[i][j] == 0) {
                    a[i][0] = 0;                        // mark this row
                    a[0][j] = 0;                        // mark this column
                }
            }
        }

        // PASS 2 — wipe the inner submatrix using the markers.
        // Go from bottom-right so we don't overwrite markers we still need.
        for (int i = m - 1; i >= 1; i--) {
            for (int j = n - 1; j >= 1; j--) {
                if (a[i][0] == 0 || a[0][j] == 0) a[i][j] = 0;
            }
        }

        // PASS 3 — handle row 0 using its own marker (a[0][0]).
        if (a[0][0] == 0) Arrays.fill(a[0], 0);

        // PASS 4 — handle column 0 using the separate flag.
        if (firstColHasZero) {
            for (int i = 0; i < m; i++) a[i][0] = 0;
        }
    }

    // ------------------------------------------------------------------
    // BONUS — the "warm-up" answer to say first, then optimize.
    // O(m*n) time, O(m + n) space.
    // ------------------------------------------------------------------
    public static void setZeroesExtraSpace(int[][] a) {
        int m = a.length, n = a[0].length;
        boolean[] rowZero = new boolean[m];
        boolean[] colZero = new boolean[n];

        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                if (a[i][j] == 0) { rowZero[i] = true; colZero[j] = true; }
            }
        }
        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                if (rowZero[i] || colZero[j]) a[i][j] = 0;
            }
        }
    }
}
