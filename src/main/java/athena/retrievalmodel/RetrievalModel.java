package athena.retrievalmodel;

import java.util.HashMap;

public interface RetrievalModel {
    HashMap<String, Double> getRanking(String query, Integer queryID);

    String getModelName();

    void printN(HashMap<String, Double> hashMap, Integer queryID, String query);
}
