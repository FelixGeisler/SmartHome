package org.felixgeisler.smarthome.capability;

/**
 * A color as CIE 1931 xy chromaticity.
 *
 * @param x the x chromaticity coordinate (0..1)
 * @param y the y chromaticity coordinate (0..1)
 */
public record XyColor(double x, double y) {}
