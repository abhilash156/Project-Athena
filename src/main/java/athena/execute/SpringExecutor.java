package athena.execute;

public class SpringExecutor {
    public static void main(String[] args) {
        SpringConfigurator configurator = new SpringConfigurator();
        //configurator.execStemFile("cacm_stem.txt");
//        configurator.generateIndex();
        configurator.executeQuerySearching();
        configurator.execEval();
    }
}
