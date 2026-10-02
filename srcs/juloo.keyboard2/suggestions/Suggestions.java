package juloo.keyboard2.suggestions;

import java.util.ArrayList;
import java.util.List;
import juloo.cdict.Cdict;
import juloo.keyboard2.dict.Dictionaries;
import juloo.keyboard2.Config;
import juloo.keyboard2.ComposeKey;
import juloo.keyboard2.ComposeKeyData;
import juloo.keyboard2.KeyboardData;
import juloo.keyboard2.PersonalDictionary;

/** Keep track of the word being typed and provide suggestions for
    [CandidatesView]. */
public final class Suggestions
{
  Callback _callback;
  Config _config;
  boolean _enabled;

  /** Current suggestions. The best suggestion is at index [0]. */
  public String[] suggestions = new String[MAX_COUNT];
  /** Number of suggestions at the beginning of the [suggestions] array that
      are not [null]. */
  public int count = 0;
  public String emoji_suggestion = null;
  /** Word entered by the space bar, or null when the typed word should be
      kept. */
  public String autocorrect = null;
  /** Number of suggestions in [suggestions]. */
  public static final int MAX_COUNT = 3;

  /** Weight of the correction cost against the word frequency (0 to 15) when
      ranking corrections. One full edit (cost 2) outweighs 8 frequency
      points. */
  static final int RANK_COST_WEIGHT = 4;
  /** The best correction is used for autocorrect only if it ranks at least
      this much better than the second. */
  static final int AUTOCORRECT_MARGIN = 2;
  /** Number of corrections requested from the dictionary. */
  static final int CORRECTIONS_COUNT = 8;
  /** Frequency given to personal dictionary words when ranking. */
  static final int PERSONAL_FREQ = 15;

  /** Maximum correction cost for a typed word of length [len]. */
  static int max_cost(int len)
  {
    if (len <= 2) return 1;
    if (len <= 4) return 2;
    if (len <= 7) return 3;
    return 4;
  }

  /** Keyboard proximity of the last text layout, for [Cdict.correct]. */
  byte[] _proximity = null;
  KeyboardData _proximity_layout = null;

  /** Buffers reused across queries. */
  final int[] _corr_idx = new int[CORRECTIONS_COUNT];
  final int[] _corr_cost = new int[CORRECTIONS_COUNT];
  /** Correction candidates, sorted by ascending rank. */
  final List<String> _cand_words = new ArrayList<String>();
  final List<Integer> _cand_ranks = new ArrayList<Integer>();
  final List<String> _pd_words = new ArrayList<String>();
  final List<Integer> _pd_costs = new ArrayList<Integer>();

  public Suggestions(Callback c, Config conf)
  {
    _callback = c;
    _config = conf;
  }

  public void started()
  {
    _enabled = _config.editor_config.should_show_candidates_view;
    clear();
  }

  /** Called when the keyboard layout changes. Layouts that are not text
      layouts (numeric, emoji) keep the proximity of the last text layout. */
  public void set_layout(KeyboardData kw)
  {
    if (kw == _proximity_layout)
      return;
    _proximity_layout = kw;
    byte[] p = Proximity.of_layout(kw);
    if (p != null)
      _proximity = p;
  }

  public void currently_typed_word(String word)
  {
    if (!_enabled)
      return;
    boolean has_personal =
      _config.personal_dictionary != null
      && !_config.personal_dictionary.is_empty();
    if (word.length() < 2
        || (_config.current_dictionary == null && !has_personal))
      clear();
    else
      query_suggestions(word);
    _callback.set_suggestions(this);
  }

  void clear()
  {
    count = 0;
    for (int i = 0; i < MAX_COUNT; i++)
      suggestions[i] = null;
    emoji_suggestion = null;
    autocorrect = null;
  }

  int query_suggestions(String word)
  {
    clear();
    int i = 0;
    boolean first_char_upper = Character.isUpperCase(word.charAt(0));
    String subst = apply_substitutions(word);
    Cdict dict = _config.current_dictionary;
    // Personal dictionary entries are matched against the raw typed word;
    // they are normalized with the same substitutions when the dictionary is
    // loaded.
    PersonalDictionary pd = _config.personal_dictionary;
    // Shortcut expansions take the first slots and are entered by the space
    // bar.
    if (pd != null)
    {
      List<String> shortcuts = pd.query_shortcuts(word, MAX_COUNT);
      for (int j = 0; j < shortcuts.size() && i < MAX_COUNT; j++)
        suggestions[i++] = shortcuts.get(j);
      if (i > 0)
        autocorrect = suggestions[0];
    }
    // Exact match. A personal word protects the typed word from autocorrect.
    Cdict.Result r = (dict != null) ? dict.find(subst) : null;
    int exact_index = (r != null && r.found) ? r.index : -1;
    String personal_exact = (pd != null) ? pd.find_word(word) : null;
    boolean valid = exact_index >= 0 || personal_exact != null;
    String exact = null;
    if (personal_exact != null)
      exact = cap(personal_exact, first_char_upper);
    else if (exact_index >= 0)
      exact = cap(dict.word(exact_index), first_char_upper);
    if (exact != null)
    {
      i = add_suggestion(i, exact);
      // Fix the case or the diacritics of a known word.
      if (autocorrect == null && !exact.equals(word))
        autocorrect = exact;
    }
    // Correction candidates from both dictionaries.
    _cand_words.clear();
    _cand_ranks.clear();
    int max_cost = max_cost(subst.length());
    if (dict != null)
    {
      int n = dict.correct(subst, _proximity, max_cost, RANK_COST_WEIGHT,
          _corr_idx, _corr_cost);
      for (int k = 0; k < n; k++)
        if (_corr_idx[k] != exact_index)
          add_candidate(dict.word(_corr_idx[k]),
              _corr_cost[k] * RANK_COST_WEIGHT - dict.freq(_corr_idx[k]));
    }
    if (pd != null)
    {
      _pd_words.clear();
      _pd_costs.clear();
      pd.query_corrections(word, _proximity, max_cost, _pd_words, _pd_costs);
      for (int k = 0; k < _pd_words.size(); k++)
        add_candidate(_pd_words.get(k),
            _pd_costs.get(k) * RANK_COST_WEIGHT - PERSONAL_FREQ);
    }
    // Autocorrect only an unknown word, only when the best correction is
    // clearly better than the others and never to complete the typed word.
    if (autocorrect == null && !valid)
    {
      int best = -1, second = -1;
      for (int k = 0; k < _cand_words.size() && second < 0; k++)
      {
        if (apply_substitutions(_cand_words.get(k)).startsWith(subst))
          continue;
        if (best < 0) best = k; else second = k;
      }
      if (best >= 0 && (second < 0
            || _cand_ranks.get(second) - _cand_ranks.get(best)
              >= AUTOCORRECT_MARGIN))
      {
        autocorrect = cap(_cand_words.get(best), first_char_upper);
        i = add_suggestion(i, autocorrect);
      }
    }
    // Fill the remaining slots alternating completions and corrections.
    int[] suffixes = (r != null) ? dict.suffixes(r, MAX_COUNT) : NO_RESULTS;
    int si = 0, ci = 0;
    boolean completion = true;
    while (i < MAX_COUNT)
    {
      boolean completions_left = si < suffixes.length;
      boolean corrections_left = ci < _cand_words.size();
      if (!completions_left && !corrections_left)
        break;
      // Take from the other source when the current one is exhausted.
      if (completion ? !completions_left : !corrections_left)
        completion = !completion;
      String w;
      if (completion)
      {
        int idx = suffixes[si++];
        if (idx == exact_index)
          continue;
        w = cap(dict.word(idx), first_char_upper);
      }
      else
        w = cap(_cand_words.get(ci++), first_char_upper);
      if (contains_ignore_case(i, w))
        continue;
      suggestions[i++] = w;
      completion = !completion;
    }
    // Personal word matches fill the slots left unused.
    if (pd != null && i < MAX_COUNT)
    {
      List<String> word_matches =
        pd.query_word_matches(word, first_char_upper, MAX_COUNT);
      for (int j = 0; j < word_matches.size() && i < MAX_COUNT; j++)
        i = add_suggestion(i, word_matches.get(j));
    }
    emoji_suggestion = query_emoji(subst);
    count = i;
    return i;
  }

  /** Add [w] at index [i] unless already present. Returns the new count. */
  int add_suggestion(int i, String w)
  {
    if (i >= MAX_COUNT || contains_ignore_case(i, w))
      return i;
    suggestions[i] = w;
    return i + 1;
  }

  /** Insert a correction candidate, keeping [_cand_words] sorted by ascending
      rank and stable for equal ranks. A case-insensitive duplicate keeps its
      best rank. */
  void add_candidate(String w, int rank)
  {
    for (int k = 0; k < _cand_words.size(); k++)
    {
      if (!_cand_words.get(k).equalsIgnoreCase(w))
        continue;
      if (_cand_ranks.get(k) <= rank)
        return;
      _cand_words.remove(k);
      _cand_ranks.remove(k);
      break;
    }
    int k = _cand_words.size();
    while (k > 0 && _cand_ranks.get(k - 1) > rank)
      k--;
    _cand_words.add(k, w);
    _cand_ranks.add(k, rank);
  }

  static String cap(String w, boolean first_char_upper)
  {
    if (!first_char_upper || w.isEmpty())
      return w;
    return w.substring(0, 1).toUpperCase() + w.substring(1);
  }

  boolean contains_ignore_case(int upto, String w)
  {
    for (int k = 0; k < upto; k++)
      if (suggestions[k] != null && suggestions[k].equalsIgnoreCase(w))
        return true;
    return false;
  }

  String query_emoji(String word)
  {
    Cdict dict = _config.emoji_dictionary;
    // Disable emoji suggestion for short words
    if (dict == null || word.length() < 3)
      return null;
    Cdict.Result r = dict.find(word);
    if (r.found)
      return dict.word(r.index);
    int[] s = dict.suffixes(r, 1);
    if (s.length > 0)
      return dict.word(s[0]);
    return null;
  }

  /** Apply the same substitutions that were used when building the
      dictionaries to find word aliases. This catches missing diacritics for
      example. */
  String apply_substitutions(String w)
  {
    StringBuilder b = new StringBuilder(w);
    int len = w.length();
    for (int i = 0; i < len; i++)
    {
      char r =
        ComposeKey.transform_char(ComposeKeyData.substitutions, b.charAt(i));
      if (r != 0) b.setCharAt(i, r);
    }
    return b.toString();
  }

  static final int[] NO_RESULTS = new int[0];

  public static interface Callback
  {
    public void set_suggestions(Suggestions suggestions);
  }
}
