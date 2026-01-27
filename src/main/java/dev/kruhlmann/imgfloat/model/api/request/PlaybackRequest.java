package dev.kruhlmann.imgfloat.model.api.request;

public class PlaybackRequest {

    private Boolean play;

    public Boolean getPlay() {
        return play == null ? Boolean.TRUE : play;
    }

    public void setPlay(Boolean play) {
        this.play = play;
    }
}
