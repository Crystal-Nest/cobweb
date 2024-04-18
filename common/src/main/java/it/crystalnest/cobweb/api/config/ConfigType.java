package it.crystalnest.cobweb.api.config;

public enum ConfigType {
  SERVER,
  CLIENT,
  COMMON;

  @Override
  public String toString() {
    return this.name().toLowerCase();
  }
}
