package com.example.audio;

public interface AudioService {

    void play(SoundEffect soundEffect);

    void setEnabled(boolean enabled);

    void setSfxVolume(double volume);

    void setMenuMusicVolume(double volume);

    default void setMusicVolume(double volume) {
        setMenuMusicVolume(volume);
    }

    void playMenuMusic();

    void stopMusic();
}
