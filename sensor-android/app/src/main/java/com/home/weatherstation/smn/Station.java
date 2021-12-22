package com.home.weatherstation.smn;

//import com.wordnik.swagger.annotations.ApiModel;
//import com.wordnik.swagger.annotations.ApiModelProperty;

//import lombok.AllArgsConstructor;
//import lombok.Getter;

/**
 * Representation of an SMN observation station.
 */
//@Getter
//@AllArgsConstructor
//@ApiModel(value = "SMN station meta data")
public class Station {
    //  @ApiModelProperty(value = "3-char all upper-case station code")
    private final String code;
    //  @ApiModelProperty(value = "Original name in local language")
    private final String name;
    //  @ApiModelProperty(value = "CH1903 (Swiss grid) y-axis value")
    private final int ch1903Y;
    //  @ApiModelProperty(value = "CH1903 (Swiss grid) x-axis value")
    private final int ch1903X;
    //  @ApiModelProperty(value = "WGS84 latitude")
    private final double lat;
    //  @ApiModelProperty(value = "WGS84 longitude")
    private final double lng;
    //  @ApiModelProperty(value = "meters above sea level")
    private final int elevation;

    public Station(String code, String name, int ch1903Y, int ch1903X, double lat, double lng, int elevation) {
        this.code = code;
        this.name = name;
        this.ch1903Y = ch1903Y;
        this.ch1903X = ch1903X;
        this.lat = lat;
        this.lng = lng;
        this.elevation = elevation;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public int getCh1903Y() {
        return ch1903Y;
    }

    public int getCh1903X() {
        return ch1903X;
    }

    public double getLat() {
        return lat;
    }

    public double getLng() {
        return lng;
    }

    public int getElevation() {
        return elevation;
    }
}
