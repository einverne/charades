package com.pongo.charades.models;

import io.realm.RealmObject;
import io.realm.annotations.PrimaryKey;

/**
 * Created by rsaki on 1/3/2016.
 */
public class CategoryTagModel extends RealmObject {
    @PrimaryKey
    private String value;

    public CategoryTagModel() {}

    public CategoryTagModel(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }
}
