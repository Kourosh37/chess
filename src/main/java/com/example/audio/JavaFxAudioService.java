package com.example.audio;

import com.example.config.AppSettings;
import javafx.scene.media.AudioClip;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;

import java.net.URL;
import java.util.EnumMap;
import java.util.Map;

public class JavaFxAudioService implements AudioService {

    private final Map<SoundEffect, AudioClip> clips = new EnumMap<>(SoundEffect.class);
    private MediaPlayer menuMusicPlayer;
    private MediaPlayer activeMusicPlayer;
    private boolean enabled;
    private double sfxVolume;
    private double menuMusicVolume;

    public JavaFxAudioService(AppSettings settings) {
        this.enabled = settings.soundEnabledProperty().get();
        this.sfxVolume = settings.sfxVolumeProperty().get();
        this.menuMusicVolume = settings.menuMusicVolumeProperty().get();

        settings.soundEnabledProperty().addListener((obs, oldValue, newValue) -> setEnabled(newValue));
        settings.sfxVolumeProperty().addListener((obs, oldValue, newValue) -> setSfxVolume(newValue.doubleValue()));
        settings.menuMusicVolumeProperty().addListener((obs, oldValue, newValue) -> setMenuMusicVolume(newValue.doubleValue()));

        load(SoundEffect.PREMOVE, "/com/example/audio/premove.mp3");
        load(SoundEffect.MOVE, "/com/example/audio/move.mp3");
        load(SoundEffect.CAPTURE, "/com/example/audio/capture.mp3");
        load(SoundEffect.CHECK, "/com/example/audio/check.mp3");
        load(SoundEffect.ILLEGAL, "/com/example/audio/illegal.mp3");
        load(SoundEffect.GAME_START, "/com/example/audio/game-start.mp3");
        load(SoundEffect.GAME_END, "/com/example/audio/game-end.mp3");
        load(SoundEffect.NOTIFY, "/com/example/audio/notify.mp3");
        menuMusicPlayer = loadBackgroundMusic("/com/example/audio/menu-background.mp3");
    }

    @Override
    public void play(SoundEffect soundEffect) {
        if (!enabled) {
            return;
        }

        AudioClip clip = clips.get(soundEffect);
        if (clip != null) {
            clip.setVolume(sfxVolume);
            clip.play();
        }
    }

    @Override
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (menuMusicPlayer != null) {
            menuMusicPlayer.setMute(!enabled);
        }
    }

    @Override
    public void setSfxVolume(double volume) {
        this.sfxVolume = clamp(volume);
    }

    @Override
    public void setMenuMusicVolume(double volume) {
        this.menuMusicVolume = clamp(volume);
        if (menuMusicPlayer != null) {
            menuMusicPlayer.setVolume(menuMusicVolume);
        }
    }

    @Override
    public void playMenuMusic() {
        switchMusic(menuMusicPlayer);
    }

    @Override
    public void stopMusic() {
        if (activeMusicPlayer != null) {
            activeMusicPlayer.pause();
        }
    }

    private void load(SoundEffect effect, String path) {
        URL resource = getClass().getResource(path);
        if (resource != null) {
            clips.put(effect, new AudioClip(resource.toExternalForm()));
        }
    }

    private MediaPlayer loadBackgroundMusic(String path) {
        URL resource = getClass().getResource(path);
        if (resource == null) {
            return null;
        }
        Media media = new Media(resource.toExternalForm());
        MediaPlayer player = new MediaPlayer(media);
        player.setCycleCount(MediaPlayer.INDEFINITE);
        player.setAutoPlay(false);
        player.setMute(!enabled);
        return player;
    }

    private double clamp(double v) {
        return Math.max(0, Math.min(1, v));
    }

    private void switchMusic(MediaPlayer target) {
        if (target == null) {
            return;
        }
        if (activeMusicPlayer != null && activeMusicPlayer != target) {
            activeMusicPlayer.pause();
        }
        activeMusicPlayer = target;
        activeMusicPlayer.setMute(!enabled);
        activeMusicPlayer.setVolume(menuMusicVolume);
        activeMusicPlayer.play();
    }
}
