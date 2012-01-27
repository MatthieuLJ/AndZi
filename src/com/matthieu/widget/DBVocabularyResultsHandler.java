/******************************
 * Interface for anything that want to process
 * results from the database as it comes
 *
 * To be used with getWordsWithSequence in HanziDB
 */
package com.matthieu.widget;

import com.matthieu.Vocabulary;
import java.util.ArrayList;

public abstract class DBVocabularyResultsHandler {
    public abstract void handle_more_results(ArrayList<Vocabulary> data);
    public abstract void handle_end_of_results();
}
