package org.obolibrary.robot;

import java.util.EnumSet;

/** Possible behaviors when writing an ontology into the OBO format. */
public enum OBOWriteOption {

  /** For each entity, drop all supernumerary labels beyond the first one. */
  DROP_EXTRA_LABELS,

  /** For each entity, drop all supernumerary definitions beyond the first one. */
  DROP_EXTRA_DEFINITIONS,

  /** For each entity, drop all supernumerary comments beyond the first one. */
  DROP_EXTRA_COMMENTS,

  /** For each entity, merge all comments (if more than one) into a single comment. */
  MERGE_COMMENTS,

  /**
   * Drop axioms that cannot be represented in OBO format, instead of writing them in a {@code
   * owl-axioms} header tag.
   */
  DROP_UNSTRANSLATABLE_AXIOMS,

  /** Drop general concept inclusion axioms. */
  DROP_GCI_AXIOMS;

  /**
   * Gets a set of OBOWriteOption from a space-separated list of keywords.
   *
   * @param s String to turn into a option set
   * @return the corresponding option set
   */
  public static EnumSet<OBOWriteOption> fromString(String s) {
    EnumSet<OBOWriteOption> set = EnumSet.noneOf(OBOWriteOption.class);
    for (String item : s.split(" ")) {
      switch (item) {
        case "drop-extra-labels":
          set.add(DROP_EXTRA_LABELS);
          break;

        case "drop-extra-definitions":
          set.add(DROP_EXTRA_DEFINITIONS);
          break;

        case "drop-extra-comments":
          set.remove(MERGE_COMMENTS);
          set.add(DROP_EXTRA_COMMENTS);
          break;

        case "merge-comments":
          set.remove(DROP_EXTRA_COMMENTS);
          set.add(MERGE_COMMENTS);
          break;

        case "drop-unstranslatable-axioms":
          set.add(DROP_UNSTRANSLATABLE_AXIOMS);
          break;

        case "drop-gci-axioms":
          set.add(DROP_GCI_AXIOMS);
          break;

        case "simple":
          // "simple" is a shortcut for all the options above except "merge-comments"
          set.add(DROP_UNSTRANSLATABLE_AXIOMS);
          set.add(DROP_GCI_AXIOMS);
          // Fall-through

        case "strict":
        case "true":
          // "strict" and "true" are shortcuts for all the "drop-extra-*" options
          set.add(DROP_EXTRA_LABELS);
          set.add(DROP_EXTRA_DEFINITIONS);
          set.add(DROP_EXTRA_COMMENTS);
          break;
      }
    }
    return set;
  }
}
