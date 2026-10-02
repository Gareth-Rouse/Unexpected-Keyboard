package juloo.keyboard2.suggestions;

import juloo.cdict.Cdict;

/** Weighted Damerau-Levenshtein (optimal string alignment) cost, with the
    same cost model as [Cdict.correct]. Used for word lists that are not
    compiled dictionaries. */
public final class EditDistance
{
  /** Cost of turning [typed] into [word], or [max_cost + 1] if it exceeds
      [max_cost]. [proximity] is a [Cdict.PROXIMITY_SIZE] table or null. */
  public static int cost(String typed, String word, byte[] proximity,
      int max_cost)
  {
    int n = typed.length();
    int m = word.length();
    if (Math.abs(n - m) * Cdict.COST_EDIT > max_cost)
      return max_cost + 1;
    // Rows over [typed], one row per char of [word].
    int[] prev2 = new int[n + 1];
    int[] prev = new int[n + 1];
    int[] row = new int[n + 1];
    for (int j = 0; j <= n; j++)
      prev[j] = j * Cdict.COST_EDIT;
    int prev_min = 0;
    for (int d = 1; d <= m; d++)
    {
      char c = word.charAt(d - 1);
      row[0] = prev[0] + Cdict.COST_EDIT;
      int row_min = row[0];
      for (int j = 1; j <= n; j++)
      {
        char t = typed.charAt(j - 1);
        int v = Math.min(prev[j], row[j - 1]) + Cdict.COST_EDIT;
        v = Math.min(v, prev[j - 1] + sub_cost(t, c, proximity));
        if (d >= 2 && j >= 2 && t == word.charAt(d - 2)
            && typed.charAt(j - 2) == c && t != c)
          v = Math.min(v, prev2[j - 2] + Cdict.COST_TRANSPOSE);
        row[j] = v;
        row_min = Math.min(row_min, v);
      }
      // The next row may transpose from [prev], bypassing this row.
      if (row_min > max_cost && prev_min + Cdict.COST_TRANSPOSE > max_cost)
        return max_cost + 1;
      prev_min = row_min;
      int[] tmp = prev2; prev2 = prev; prev = row; row = tmp;
    }
    return (prev[n] <= max_cost) ? prev[n] : max_cost + 1;
  }

  static int sub_cost(char a, char b, byte[] proximity)
  {
    if (a == b)
      return 0;
    if (proximity != null && a < 128 && b < 128 && proximity[a * 128 + b] != 0)
      return Cdict.COST_PROXIMATE;
    return Cdict.COST_EDIT;
  }
}
