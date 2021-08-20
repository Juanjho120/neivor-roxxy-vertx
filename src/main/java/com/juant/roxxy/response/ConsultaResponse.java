package com.juant.roxxy.response;

import java.util.ArrayList;
import java.util.List;

import com.juant.roxxy.entity.Pago;

/**
 * Response to be sent when /api/roxxy/condominium/search-payments API is requested
 * @author Juan Tzun
 *
 */
public class ConsultaResponse {

	private String codigoBusqueda;
	private String codigoServicio;
	private Double importeAdeudado;
	private Double importeMinimo;
	private Double importeComision;
	private String nombreCliente;
	private List<Pago> pagos;
	
	/**
	 * Constructor without parameters. Initialize all attributes with default values.
	 */
	public ConsultaResponse() {
		this.codigoBusqueda = "";
		this.codigoServicio = "";
		this.importeAdeudado = 0.0;
		this.importeMinimo = 0.0;
		this.importeComision = 0.0;
		this.nombreCliente = "";
		this.pagos = new ArrayList<>();
	}

	/**
	 * Constructor with parameters.
	 * @param codigoBusqueda Roxxy customer code
	 * @param codigoServicio Neivor payment order code
	 * @param importeAdeudado Payment order amount
	 * @param importeMinimo Payment order minimum amount
	 * @param importeComision Payment order comission (always 0)
	 * @param nombreCliente Roxxy customer name
	 * @param pagos Payment order fees
	 */
	public ConsultaResponse(String codigoBusqueda, String codigoServicio, Double importeAdeudado, Double importeMinimo,
			Double importeComision, String nombreCliente, List<Pago> pagos) {
		this.codigoBusqueda = codigoBusqueda;
		this.codigoServicio = codigoServicio;
		this.importeAdeudado = importeAdeudado;
		this.importeMinimo = importeMinimo;
		this.importeComision = importeComision;
		this.nombreCliente = nombreCliente;
		this.pagos = pagos;
	}



	public String getCodigoBusqueda() {
		return codigoBusqueda;
	}

	public void setCodigoBusqueda(String codigoBusqueda) {
		this.codigoBusqueda = codigoBusqueda;
	}

	public String getCodigoServicio() {
		return codigoServicio;
	}

	public void setCodigoServicio(String codigoServicio) {
		this.codigoServicio = codigoServicio;
	}

	public Double getImporteAdeudado() {
		return importeAdeudado;
	}

	public void setImporteAdeudado(Double importeAdeudado) {
		this.importeAdeudado = importeAdeudado;
	}

	public Double getImporteMinimo() {
		return importeMinimo;
	}

	public void setImporteMinimo(Double importeMinimo) {
		this.importeMinimo = importeMinimo;
	}

	public Double getImporteComision() {
		return importeComision;
	}

	public void setImporteComision(Double importeComision) {
		this.importeComision = importeComision;
	}

	public String getNombreCliente() {
		return nombreCliente;
	}

	public void setNombreCliente(String nombreCliente) {
		this.nombreCliente = nombreCliente;
	}

	public List<Pago> getPagos() {
		return pagos;
	}

	public void setPagos(List<Pago> pagos) {
		this.pagos = pagos;
	}
	
}
