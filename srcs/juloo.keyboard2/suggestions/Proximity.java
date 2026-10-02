package juloo.keyboard2.suggestions;

import juloo.cdict.Cdict;
import juloo.keyboard2.KeyValue;
import juloo.keyboard2.KeyboardData;

/** Which letters are neighbours on the keyboard, used to make substituting a
    neighbouring key a cheap edit in [Cdict.correct]. */
public final class Proximity
{
  /** Two keys are neighbours when their centres are at most this far apart,
      in key-width units (rows count as 1 unit high per unit of row height). */
  static final float MAX_DISTANCE = 1.5f;
  /** Layouts with fewer ASCII letter keys are not text layouts. */
  static final int MIN_LETTER_KEYS = 10;

  /** Table for [Cdict.correct], or null when [kw] is not a text layout. */
  public static byte[] of_layout(KeyboardData kw)
  {
    int max = 0;
    for (KeyboardData.Row row : kw.rows)
      max += row.keys.size();
    char[] chars = new char[max];
    float[] xs = new float[max];
    float[] ys = new float[max];
    int n = 0;
    float y = 0.f;
    for (KeyboardData.Row row : kw.rows)
    {
      float row_y = y + row.shift + row.height / 2;
      float x = 0.f;
      for (KeyboardData.Key key : row.keys)
      {
        float kx = x + key.shift + key.width / 2;
        x += key.shift + key.width;
        KeyValue kv = key.keys[0];
        if (kv == null || kv.getKind() != KeyValue.Kind.Char)
          continue;
        char c = Character.toLowerCase(kv.getChar());
        if (!Character.isLetter(c) || c >= 128)
          continue;
        chars[n] = c;
        xs[n] = kx;
        ys[n] = row_y;
        n++;
      }
      y += row.shift + row.height;
    }
    if (n < MIN_LETTER_KEYS)
      return null;
    return of_key_centers(chars, xs, ys, n);
  }

  /** Symmetric table from key centres; [chars] are lower-case ASCII. */
  static byte[] of_key_centers(char[] chars, float[] xs, float[] ys, int n)
  {
    byte[] t = new byte[Cdict.PROXIMITY_SIZE];
    for (int i = 0; i < n; i++)
      for (int j = i + 1; j < n; j++)
      {
        if (chars[i] == chars[j]
            || Math.hypot(xs[i] - xs[j], ys[i] - ys[j]) > MAX_DISTANCE)
          continue;
        t[chars[i] * 128 + chars[j]] = 1;
        t[chars[j] * 128 + chars[i]] = 1;
      }
    return t;
  }
}
