package com.juant.roxxy.entity;

/**
 * Entity class to map detalles_pagos table from Roxxy database
 * @author Juan Tzun
 *
 */
public class Pago {

	private Integer numeroCuota;
	private String detalleCuota;
	private String fechaVencimiento;
	private Double importeCuota;
	private Double importeComision;
	
	/**
	 * Constructor without parameters. Initialize all attributes with default values.
	 */
	public Pago() {
		this.numeroCuota = 0;
		this.detalleCuota = "";
		this.fechaVencimiento = "";
		this.importeCuota = 0.0;
		this.importeComision = 0.0;
	}

	/**
	 * Constructor with parameters.
	 * @param numeroCuota Payment fee number
	 * @param detalleCuota Payment fee description
	 * @param fechaVencimiento Payment fee expiration date
	 * @param importeCuota Payment fee amount
	 * @param importeComision Payment fee comission
	 */
	public Pago(Integer numeroCuota, String detalleCuota, String fechaVencimiento, Double importeCuota,
			Double importeComision) {
		this.numeroCuota = numeroCuota;
		this.detalleCuota = detalleCuota;
		this.fechaVencimiento = fechaVencimiento;
		this.importeCuota = importeCuota;
		this.importeComision = importeComision;
	}



	public Integer getNumeroCuota() {
		return numeroCuota;
	}

	public void setNumeroCuota(Integer numeroCuota) {
		this.numeroCuota = numeroCuota;
	}

	public String getDetalleCuota() {
		return detalleCuota;
	}

	public void setDetalleCuota(String detalleCuota) {
		this.detalleCuota = detalleCuota;
	}

	public String getFechaVencimiento() {
		return fechaVencimiento;
	}

	public void setFechaVencimiento(String fechaVencimiento) {
		this.fechaVencimiento = fechaVencimiento;
	}

	public Double getImporteCuota() {
		return importeCuota;
	}

	public void setImporteCuota(Double importeCuota) {
		this.importeCuota = importeCuota;
	}

	public Double getImporteComision() {
		return importeComision;
	}

	public void setImporteComision(Double importeComision) {
		this.importeComision = importeComision;
	}

}
