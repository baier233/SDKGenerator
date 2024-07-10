package me.baier.config;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.io.File;

@Getter
@Setter
@AllArgsConstructor
public class Settings {
	private File OUT_PUT_DIR;
	private String FILTER_PACKAGE_PATH;
}
