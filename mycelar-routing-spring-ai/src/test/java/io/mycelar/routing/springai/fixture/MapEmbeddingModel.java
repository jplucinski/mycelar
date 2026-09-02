package io.mycelar.routing.springai.fixture;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

public final class MapEmbeddingModel implements EmbeddingModel {

    private final Map<String, float[]> vectors = new LinkedHashMap<>();
    private final AtomicInteger calls = new AtomicInteger();
    private final List<Integer> batchSizes = new ArrayList<>();
    private RuntimeException failure;

    public MapEmbeddingModel put(String text, float... vector) {
        vectors.put(text, vector);
        return this;
    }

    public MapEmbeddingModel failWith(RuntimeException failure) {
        this.failure = failure;
        return this;
    }

    public int calls() {
        return calls.get();
    }

    public List<Integer> batchSizes() {
        return List.copyOf(batchSizes);
    }

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        calls.incrementAndGet();
        List<String> inputs = request.getInstructions();
        batchSizes.add(inputs.size());
        if (failure != null) {
            throw failure;
        }
        List<Embedding> results = new ArrayList<>();
        for (int i = 0; i < inputs.size(); i++) {
            String text = inputs.get(i);
            float[] vector = vectors.get(text);
            if (vector == null) {
                throw new IllegalStateException("no vector for: " + text);
            }
            results.add(new Embedding(vector, i));
        }
        return new EmbeddingResponse(results);
    }

    @Override
    public float[] embed(Document document) {
        return embed(document.getText());
    }
}
