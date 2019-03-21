package com.sematext.logseneandroid.logsene;

import okhttp3.RequestBody;

import java.util.ArrayList;
import java.util.List;

import com.sematext.logseneandroid.Utils;

/**
 * Represents a bulk request.
 */
public class Bulk {
  private final List<String> sources;
  private final List<String> types;

  /**
   * Constructor.
   * @param sources list of sources
   * @param types list of types (one for each source)
   * @throws IllegalArgumentException if sources and types don't have the same size
   */
  public Bulk(List<String> sources, List<String> types) {
    Utils.requireNonNull(sources);
    Utils.requireNonNull(types);
    if (sources.size() != types.size()) {
      throw new IllegalArgumentException("sources and types should have the same size");
    }
    this.sources = sources;
    this.types = types;
  }

  /**
   * Returns the request body for use with http client.
   *
   * @param index the index to use for all documents
   * @return the request body
   */
  public RequestBody toBody(String index) {
    return RequestBody.create(LogseneClient.JSON, this.toString(index));
  }

  /**
   * Returns the request as string.
   *
   * @param index the index to use for all documents
   * @return the request body as string
   */
  public String toString(String index) {
    Utils.requireNonNull(index);
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < sources.size(); i++) {
      sb.append(String.format("{ \"index\" : { \"_index\": \"%s\", \"_type\" : \"%s\" } }\n", index, types.get(i)));
      sb.append(sources.get(i).trim() + "\n");
    }
    return sb.toString();
  }

  /**
   * Builder for bulk requests.
   */
  public static class Builder {
    public List<String> sources = new ArrayList<>();
    public List<String> types = new ArrayList<>();

    /**
     * Adds another source and type combination.
     * @param source the source data (json)
     * @param type the type to use when indexing
     * @return the builder
     */
    public Builder addSource(String source, String type) {
      Utils.requireNonNull(source);
      Utils.requireNonNull(type);
      sources.add(source);
      types.add(type);
      return this;
    }

    /**
     * Builds the bulk request.
     * @return the bulk request
     */
    public Bulk build() {
      return new Bulk(sources, types);
    }
  }

}
