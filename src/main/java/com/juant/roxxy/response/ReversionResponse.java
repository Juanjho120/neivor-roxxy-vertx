package com.juant.roxxy.response;

/**
 * Response to be sent when /api/roxxy/condominium/payment-reversion API is requested
 * @author Juan Tzun
 *
 */
public class ReversionResponse {

	private String codigoBusqueda;
	private Long idTxnNeivorResponse;
	private Long idTxnRevertida;
	
	/**
	 * Constructor without parameters. Initialize all attributes with default values.
	 */
	public ReversionResponse() {
		this.codigoBusqueda = "";
		this.idTxnNeivorResponse = 0L;
		this.idTxnRevertida = 0L;
	}

	/**
	 * Constructor with parameters.
	 * @param codigoBusqueda Roxxy customer code
	 * @param idTxnNeivorResponse Neivor reversion id
	 * @param idTxnRevertida Roxxy reversion id
	 */
	public ReversionResponse(String codigoBusqueda, Long idTxnNeivorResponse, Long idTxnRevertida) {
		this.codigoBusqueda = codigoBusqueda;
		this.idTxnNeivorResponse = idTxnNeivorResponse;
		this.idTxnRevertida = idTxnRevertida;
	}

	public String getCodigoBusqueda() {
		return codigoBusqueda;
	}

	public void setCodigoBusqueda(String codigoBusqueda) {
		this.codigoBusqueda = codigoBusqueda;
	}

	public Long getIdTxnNeivorResponse() {
		return idTxnNeivorResponse;
	}

	public void setIdTxnNeivorResponse(Long idTxnNeivorResponse) {
		this.idTxnNeivorResponse = idTxnNeivorResponse;
	}

	public Long getIdTxnRevertida() {
		return idTxnRevertida;
	}

	public void setIdTxnRevertida(Long idTxnRevertida) {
		this.idTxnRevertida = idTxnRevertida;
	}
	
}
