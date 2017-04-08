package athena.index;

import athena.crawler.CrawlerUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.*;
import java.util.*;

@Component
public class InvertedIndexer {

    @Value("${search.engine.ngrams}")
    private Integer nGrams;
    @Value("${search.engine.enable.case.fold}")
    private Boolean doCaseFold;
    @Value("${search.engine.remove.noise.factor}")
    private Integer noiseFactor;

    private String indexFolder;
    private String dataFolder;

    private final static String FOLDER_INDEX = "\\Athena\\Index\\";
    private final static String FILE_ENCODING = "UTF-8";
    private final static String STRING_REPLACEMENT = " ";

    @Autowired
    private CrawlerUtils crawlerUtils;

    public InvertedIndexer() {
        this.indexFolder = FOLDER_INDEX;
        setDataFolder(indexFolder);
    }

    public String getIndexFolder() {
        return indexFolder;
    }

    public void setIndexFolder(String indexFolder) {
        this.indexFolder = indexFolder;
        setDataFolder(indexFolder);
    }

    private void setDataFolder(String indexFolder) {
        this.dataFolder = indexFolder + "\\DataFiles\\";
    }

    public String getDataFolder() {
        return dataFolder;
    }

    private void tokenizeHTMLFiles(String folderPath) {
        String content;
        try {
            File folder = new File(folderPath);
            File[] files = folder.listFiles();
            if (files == null) {
                System.out.println("No files present or invalid folder");
            } else {
                for (File file : files) {
                    content = Jsoup.parse(file, FILE_ENCODING).text();
                    if (doCaseFold) {
                        content = caseFoldText(content);
                    }
                    for (int i = 0; i < noiseFactor; i++) {
                        content = removeNoise(content);
                    }
                    createTokenizedFile(formatFileName(file.getName()), content);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String formatFileName(String fileName) {
        return StringUtils.remove(fileName, ".html");
    }

    private String caseFoldText(String text) {
        return text.toLowerCase();
    }

    //TODO: Time is not getting preserved. Do we have to?
    //TODO: Not Preserving "," not relevant with Casm
    private String removeNoise(String text) {
        text = StringUtils.replaceAll(text, "(\\[[0-9]\\w{0,3}\\])", STRING_REPLACEMENT);
        text = StringUtils.replaceAll(text, "(\\.+ )|(\\-+ )|( -+)|( \\.+)|(^-)|(^\\.)", STRING_REPLACEMENT);
        text = StringUtils.replaceAll(text, "(\\.+ )", STRING_REPLACEMENT);
        text = StringUtils.replaceAll(text, "(\\-+ )", STRING_REPLACEMENT);
        text = StringUtils.replaceAll(text, "( -+)", STRING_REPLACEMENT);
        text = StringUtils.replaceAll(text, "( \\.+)", STRING_REPLACEMENT);
        text = StringUtils.replaceAll(text, "(^-)", STRING_REPLACEMENT);
        text = StringUtils.replaceAll(text, "(^\\.)", STRING_REPLACEMENT);
        text = StringUtils.replaceAll(text, "([^0-9a-zA-Z\\.\\- ])", STRING_REPLACEMENT);
        return StringUtils.replace(text, "  ", STRING_REPLACEMENT);
    }

    public void createIndex(String inputFolder) {
        tokenizeHTMLFiles(inputFolder);

        HashMap<String, HashMap<String, Integer>> index = new HashMap<>();
        HashMap<String, Integer> terms;
        HashMap<String, Integer> tokenCountMap = new HashMap<>();
        List<String> words;
        int tokenCount;
        String word;
        try {
            File folder = new File(dataFolder);
            File[] files = folder.listFiles();
            for (File file : files) {
                String documentID = StringUtils.remove(file.getName(), ".txt");
                FileReader fileReader = new FileReader(file);
                BufferedReader br = new BufferedReader(fileReader);
                String currentLine = br.readLine();
                if (currentLine != null) {
                    words = getValidWords(currentLine.split(" "));
                    tokenCount = words.size() - nGrams;
                    for (int i = 0; i <= tokenCount; i++) {
                        word = words.get(i);
                        for (int j = 1; j < nGrams; j++) {
                            word = word + " " + words.get(i + j);
                        }
                        if (index.containsKey(word)) {
                            terms = index.get(word);
                            if (terms.keySet().contains(documentID)) {
                                terms.put(documentID, terms.get(documentID) + 1);
                            } else {
                                terms.put(documentID, 1);
                            }
                            index.put(word, terms);
                        } else {
                            terms = new HashMap<>();
                            terms.put(documentID, 1);
                            index.put(word, terms);
                        }
                    }
                    tokenCountMap.put(documentID, tokenCount);
                }
            }
            System.out.println("Index Size : " + index.size());
            writeIndexToJsonFile(index);
        } catch (IOException e) {
            e.printStackTrace();
        }
        writeTokenCountToJsonFile(tokenCountMap);
    }

    private List<String> getValidWords(String[] strings) {
        List<String> validWords = new ArrayList<>();
        Integer wordLength;
        String tempString;
        for (String word : strings) {
            wordLength = word.length();
            if (wordLength != 0) {
                if (wordLength == 1) {
                    if (!word.equals(".")) {
                        validWords.add(word);
                    }
                } else {
                    if (word.charAt(wordLength - 1) == '.') {
                        //System.out.println(word);
                        tempString = word.substring(0, wordLength - 2);
                        if (tempString.length() > 0) {
                            validWords.add(tempString);
                        }
                    } else {
                        validWords.add(word);
                    }
                }
            }
        }
        return validWords;
    }

    private LinkedHashMap<String, Integer> sortTermFrequency(HashMap<String, Integer> hashMap) {
        List<Map.Entry<String, Integer>> entries = new ArrayList<>(hashMap.entrySet());
        entries.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));

        LinkedHashMap<String, Integer> sortedMap = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> element : entries) {
            sortedMap.put(element.getKey(), element.getValue());
        }
        return sortedMap;
    }

    private List<List<String>> sortDocumentFrequency(List<List<String>> documentFrequency) {
        documentFrequency.sort(Comparator.comparing(a -> a.get(0)));
        return documentFrequency;
    }

    private void writeIndexToJsonFile(HashMap<String, HashMap<String, Integer>> index) {
        writeToJsonFile(index, "Index_" + nGrams);
    }

    private void writeTokenCountToJsonFile(HashMap<String, Integer> token) {
        writeToJsonFile(token, "TokenCount_" + nGrams);
    }

    private void writeToJsonFile(Object object, String fileName) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            File file = new File(indexFolder + fileName + ".json");
            mapper.writerWithDefaultPrettyPrinter().writeValue(file, object);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void createTokenizedFile(String filename, String content) {
        crawlerUtils.writeToFile(dataFolder, filename, content, CrawlerUtils.TEXT_FILE);
    }

    private Object readFromJsonFile(String fileName) {
        Object object = new Object();
        try {
            ObjectMapper mapper = new ObjectMapper();
            File file = new File(indexFolder + fileName + ".json");
            object = mapper.readValue(file, Object.class);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return object;
    }

    public HashMap<String, Integer> generateTermFrequencyTable(HashMap<String, HashMap<String, Integer>> index) {
        HashMap<String, Integer> termFrequencyMap = new HashMap<>();
        Set<String> indexKeySet = index.keySet();
        HashMap<String, Integer> terms;
        Integer termFrequency;
        Set<String> termKeySet;
        for (String s : indexKeySet) {
            terms = index.get(s);
            termKeySet = terms.keySet();
            termFrequency = 0;
            for (String t : termKeySet) {
                termFrequency += terms.get(t);
            }
            termFrequencyMap.put(s, termFrequency);
        }
        return sortTermFrequency(termFrequencyMap);
    }

    public List<List<String>> generateDocumentFrequencyTable(HashMap<String, HashMap<String, Integer>> index) {
        List<List<String>> documentFrequencyList = new ArrayList<>();
        Set<String> indexKeySet = index.keySet();
        HashMap<String, Integer> terms;
        String documentIDs;
        Set<String> termKeySet;
        for (String s : indexKeySet) {
            List<String> list = new ArrayList<>();
            list.add(s);
            terms = index.get(s);
            termKeySet = terms.keySet();
            documentIDs = "";
            termKeySet.toArray();
            for (String t : termKeySet) {
                documentIDs = documentIDs + t + ", ";
            }
            documentIDs = documentIDs.substring(0, (documentIDs.length() - 2));
            list.add(documentIDs);
            list.add(termKeySet.size() + "");
            documentFrequencyList.add(list);
        }
        return sortDocumentFrequency(documentFrequencyList);
    }

    public HashMap<String, HashMap<String, Integer>> readIndexFromJsonFile() {
        return (HashMap<String, HashMap<String, Integer>>) readFromJsonFile("Index_" + nGrams);
    }

    public HashMap<String, Integer> readTokenCountToJsonFile() {
        return (HashMap<String, Integer>) readFromJsonFile("TokenCount_" + nGrams);
    }

    public void generateTermFrequencyCSV(HashMap<String, Integer> hashMap, String fileName) {
        try {
            CSVFormat csvFormat = CSVFormat.EXCEL;
            File file = new File(indexFolder + fileName + ".csv");
            FileWriter fileWriter = new FileWriter(file);
            CSVPrinter csvPrinter = new CSVPrinter(fileWriter, csvFormat);
            Set<String> hashKeySet = hashMap.keySet();
            List<String> records = new ArrayList<>();
            //records.add("Rank");
            records.add("Term");
            records.add("Frequency");
            csvPrinter.printRecord(records);
            for (String s : hashKeySet) {
                records = new ArrayList<>();
                records.add(s);
                records.add(hashMap.get(s) + "");
                csvPrinter.printRecord(records);
            }
            fileWriter.close();
            csvPrinter.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void generateDocumentFrequencyCSV(List<List<String>> docList, String fileName) {
        try {
            CSVFormat csvFormat = CSVFormat.EXCEL;
            File file = new File(indexFolder + fileName + ".csv");
            FileWriter fileWriter = new FileWriter(file);
            CSVPrinter csvPrinter = new CSVPrinter(fileWriter, csvFormat);
            List<String> record = new ArrayList<>();
            record.add("Term");
            record.add("DocumentID");
            record.add("DocumentFrequency");
            csvPrinter.printRecord(record);
            for (List<String> record2 : docList) {
                csvPrinter.printRecord(record2);
            }
            fileWriter.close();
            csvPrinter.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void generateTermFrequencyLogCSV(HashMap<String, Integer> hashMap, String fileName) {
        try {
            CSVFormat csvFormat = CSVFormat.EXCEL;
            File file = new File(indexFolder + fileName + ".csv");
            FileWriter fileWriter = new FileWriter(file);
            CSVPrinter csvPrinter = new CSVPrinter(fileWriter, csvFormat);
            Set<String> hashKeySet = hashMap.keySet();
            List<String> records = new ArrayList<>();
            //records.add("Term");
            records.add("Log(Rank)");
            records.add("Log(Frequency)");
            csvPrinter.printRecord(records);
            int i = 1;
            for (String s : hashKeySet) {
                records = new ArrayList<>();
                //records.add(s);
                records.add(Math.log(i++) + "");
                records.add(Math.log(hashMap.get(s)) + "");
                csvPrinter.printRecord(records);
            }
            fileWriter.close();
            csvPrinter.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public LinkedHashMap<Double, Double> generateTermFrequencyLog(HashMap<String, Integer> hashMap) {
        Set<String> hashKeySet = hashMap.keySet();
        LinkedHashMap<Double, Double> dataSet = new LinkedHashMap<>();
        int i = 1;
        for (String s : hashKeySet) {
            dataSet.put(Math.log(i++), Math.log(hashMap.get(s)));
        }
        return dataSet;
    }
}