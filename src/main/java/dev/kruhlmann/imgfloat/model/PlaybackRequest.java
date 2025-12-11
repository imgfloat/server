package dev.kruhlmann.imgfloat.model;

public class PlaybackRequest {
    private Boolean play;

    public Boolean getPlay() {
        return play == null ? Boolean.TRUE : play;
    }

    public void setPlay(Boolean play) {
        this.play = play;
    }
}
