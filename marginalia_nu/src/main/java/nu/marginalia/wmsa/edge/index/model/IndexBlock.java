package nu.marginalia.wmsa.edge.index.model;

public enum IndexBlock {
    TitleKeywords(0, 0),
    Title(1, 1),

    Link(2, 1.25),

    Subjects(3, 0.5),
    NamesWords(4, 5),
    Artifacts(5, 10),
    Meta(6, 7),

    Tfidf_Top(7, 2),
    Tfidf_Middle(8, 2.5),
    Tfidf_Lower(9, 5.0),

    Words_1(10, 3.0),
    Words_2(11, 3.5),
    Words_4(12, 4.0),
    Words_8(13, 4.5),
    Words_16Plus(14, 7.0),
    ;

    public final int id;
    public final double sortOrder;

    IndexBlock(int id, double sortOrder) {
        this.sortOrder = sortOrder;
        this.id = id;
    }

    public static IndexBlock byId(int id) {
        for (IndexBlock block : values()) {
            if (id == block.id) {
                return block;
            }
        }
        throw new IllegalArgumentException("Bad block id");
    }
}
