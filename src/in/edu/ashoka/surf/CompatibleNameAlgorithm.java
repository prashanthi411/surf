package in.edu.ashoka.surf;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import in.edu.ashoka.surf.util.UnionFindSet;
import edu.stanford.muse.util.Util;

import java.util.*;
import java.util.stream.Collectors;

/* A class that performs loose merging on a dataset based on a primaryField, but only when the secondaryField is exactly the same. */
public class CompatibleNameAlgorithm extends MergeAlgorithm {

    private String primaryFieldName;
    Filter filter;

	public CompatibleNameAlgorithm(Dataset d, String primaryFieldName, Filter filter) {
		super(d);
	    this.primaryFieldName = primaryFieldName;
	    this.filter = filter;

    }

    // check that each token in x maps to a token in y
	private static boolean canTokensMap (Collection<String> xTokens, Collection<String> yTokens) {
		outer:
		for (String xt: xTokens) {
			for (String yt : yTokens) {
				if (yt.startsWith(xt))
					continue outer;

				// allow yt to end with xt, but only if xt is more than 1 char!
				if (xt.length() > 2 && yt.endsWith(xt))
					continue outer;
			}
			return false;
		}
		return true;
	}

	// core compatibility function: at least 2 multi-letter tokens common, or whether all tokens in one can map to the other modulo initials
	private static int compatibility (String x, String y) {
		Set<String> xTokens = new LinkedHashSet<>(Util.tokenize(x));
		Set<String> yTokens = new LinkedHashSet<> (Util.tokenize(y));

		Set<String> multiLetterXTokens = xTokens.stream().filter (s -> s.length() > 1).collect(Collectors.toSet());
		Set<String> multiLetterYTokens = yTokens.stream().filter (s -> s.length() > 1).collect(Collectors.toSet());
		multiLetterXTokens.retainAll(multiLetterYTokens);
		if (multiLetterXTokens.size() >= 2)
			return multiLetterXTokens.size();

		if (canTokensMap(xTokens, yTokens) || canTokensMap(yTokens, xTokens))
			return 1;
		else
			return 0;
	}

	@Override
	public List<Collection<Row>> run() {
		String field = primaryFieldName;

        List<Row> filteredRows = dataset.getRows().stream().filter(filter::passes).collect(Collectors.toList());

        // setup tokenToFieldIdx: is a map of token (of at least 3 chars) -> all indexes in stnames that contain that token
		// since editDistance computation is expensive, we'll only compute it for pairs that have at least 1 token in common
		Multimap<String, Integer> tokenToFieldIdx = HashMultimap.create();
		for (int i = 0; i < filteredRows.size(); i++) {
			String fieldVal = filteredRows.get(i).get(field);
			StringTokenizer st = new StringTokenizer(fieldVal, Tokenizer.DELIMITERS);
			while (st.hasMoreTokens()) {
				String tok = st.nextToken();
				if (tok.length() < 3)
					continue;
				tokenToFieldIdx.put(tok, i);
			}
		}

		UnionFindSet<Row> ufs = new UnionFindSet<>();
		for (int i = 0; i < filteredRows.size(); i++) {
            String fieldVal = filteredRows.get(i).get(field);

            // collect the indexes of the values that we should compare stField with, based on common tokens
			Set<Integer> idxsToCompareWith = new LinkedHashSet<>();
			{
				StringTokenizer st = new StringTokenizer(fieldVal, Tokenizer.DELIMITERS);
				while (st.hasMoreTokens()) {
					String tok = st.nextToken();
					if (tok.length() < 3)
						continue;
					Collection<Integer> c = tokenToFieldIdx.get(tok);
					for (Integer j: c)
						if (j > i)
							idxsToCompareWith.add(j);
				}
			}

			for (int j : idxsToCompareWith) {
				if (compatibility (filteredRows.get(i).get(field), filteredRows.get(j).get(field)) > 0) { // note: we could also compare primary field, not st field, to reduce noise
					ufs.unify (filteredRows.get(i), filteredRows.get(j)); // i and j are in the same group
				}
			}
		}

		List<List<Row>> clusters = ufs.getClassesSortedByClassSize(); // this gives us equivalence classes of row#s that have been merged

		// now translate the row#s back to the actual rows
        classes = new ArrayList<>();
		for (List<Row> cluster : clusters) {
			classes.add (cluster);
		}
		return classes;
	}

	public static void main (String args[]) {
        Util.ASSERT (compatibility("AJMAL SIRAJUDIN", "AJMAL SERAJ UDIN") == 0);
		Util.ASSERT (compatibility("JUGAL KISOR", "JUGAL KISOR SARMA") > 0);
		Util.ASSERT (compatibility("IERAM REDI SUBA WENKATA", "I REDI SUBA W") > 0);
		Util.ASSERT (compatibility("ADWANI LAL KRISNA", "L K ADWANI") > 0);
		Util.ASSERT (compatibility("AJMAL SIRAJUDIN", "AJMAL SIRAJ UDIN") > 0);
		Util.ASSERT (compatibility("GAJENDRA SEKAWAT SING", "GAJENDRASING SEKAWAT") > 0);
		System.out.println ("All tests successful!");
	}
}
