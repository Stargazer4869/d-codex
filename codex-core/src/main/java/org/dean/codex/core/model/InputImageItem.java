package org.dean.codex.core.model;

public record InputImageItem(ModelInputRole role, String imageUrl, String detail) implements ModelInputItem {

    public InputImageItem {
        role = role == null ? ModelInputRole.USER : role;
        imageUrl = imageUrl == null ? "" : imageUrl;
        detail = detail == null ? "auto" : detail;
    }

    @Override
    public String type() {
        return "input_image";
    }
}
