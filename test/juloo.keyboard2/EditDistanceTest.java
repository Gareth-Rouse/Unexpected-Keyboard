package juloo.keyboard2;

import juloo.cdict.Cdict;
import juloo.keyboard2.suggestions.EditDistance;
import org.junit.Test;
import static org.junit.Assert.*;

public class EditDistanceTest
{
  @Test
  public void transpositions_at_cost_boundary()
  {
    String[][] pairs = {{"ot", "to"}, {"fo", "of"}, {"si", "is"}, {"ti", "it"}};
    for (String[] pair : pairs)
    {
      assertEquals(1, EditDistance.cost(pair[0], pair[1], null, 1));
      assertEquals(1, EditDistance.cost(pair[0], pair[1], null, 0));
    }
    // An ordinary substitution spends all but the transposition's cost.
    assertEquals(3, EditDistance.cost("xbced", "abcde", null, 3));
    assertEquals(3, EditDistance.cost("xbced", "abcde", null, 2));
    byte[] proximity = new byte[Cdict.PROXIMITY_SIZE];
    proximity['s' * 128 + 'a'] = 1;
    assertEquals(2, EditDistance.cost("sot", "ato", proximity, 2));
    assertEquals(2, EditDistance.cost("sot", "ato", proximity, 1));
    assertEquals(3, EditDistance.cost("sot", "ato", null, 3));
  }
}
