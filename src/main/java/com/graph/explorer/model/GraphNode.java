package com.graph.explorer.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class GraphNode {

    private String id;
    private String parentId;
    private String name;
    private String accountNumber;
    private List<NodeTransaction> transactions = new ArrayList<>();

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getParentId() {
        return parentId;
    }

    public void setParentId(String parentId) {
        this.parentId = parentId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public void setAccountNumber(String accountNumber) {
        this.accountNumber = accountNumber;
    }

    public List<NodeTransaction> getTransactions() {
        return transactions;
    }

    public void setTransactions(List<NodeTransaction> transactions) {
        this.transactions = transactions == null ? new ArrayList<>() : transactions;
    }
}
