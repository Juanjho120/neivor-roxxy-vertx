package com.juant.roxxy.handler;

import java.util.ArrayList;
import java.util.List;

import com.juant.roxxy.RoxxyVerticle;
import com.juant.roxxy.entity.Pago;
import com.juant.roxxy.response.ConsultaResponse;
import com.juant.roxxy.response.ReversionResponse;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.mysqlclient.MySQLPool;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.Tuple;

/**
 * Handles API routing for Roxxy
 * @author Juan Tzun
 *
 */
public class RoxxyHandler {
	
	/**
	 * Creates configuration for database connection and defines all available routes starting by /api/roxxy.
	 * The following APIs are available:
	 * <ul>
	 * 	<li>POST /api/condominium/search-payments (see searchPaymentsByServiceCode)</li>
	 * 	<li>POST /api/condominium/make-payment (see makePaymentForCondominium)</li>
	 * 	<li>DELETE /api/condominium/payment-reversion (see revertPaymentForCondominium)</li>
	 * </ul>
	 * @param vertx The entry point into the Vert.x Core API. 
	 * @return Router with Roxxy routes
	 */
	public Router getAPISubRouter(Vertx vertx) {
		
		Router apiSubRouter = Router.router(vertx);
		
		//API Routing
    	apiSubRouter.route("/*").handler(this::defaultProcessorForRoxxyAPI);
    	apiSubRouter.route("/condominium*").handler(BodyHandler.create());
    	apiSubRouter.post("/condominium/search-payments").handler(this::searchPaymentsByServiceCode);
    	apiSubRouter.post("/condominium/make-payment").handler(this::makePaymentForCondominium);
    	apiSubRouter.delete("/condominium/payment-reversion").handler(this::revertPaymentForCondominium);
    	
		return apiSubRouter;
	}
	
	/**
	 * Called for all default API HTTP GET, POST, PUT and DELETE. Enables cross origin
	 * The process to be executed is as follows:
	 * <ol>
	 * 	<li>User and password validation</li>
	 * 	<li>Password validation</li>
	 * 	<li>User validation</li>
	 * 	<li>Entity validation</li>
	 * 	<li>Credentials validation</li>
	 * </ol>
	 * @param routingContext Represents the context for the handling of a request in Vert.x-Web.
	 */
	public void defaultProcessorForRoxxyAPI(RoutingContext routingContext) {
    	
		//Recovering fields from headers request
    	String usuario = routingContext.request().headers().get("usuario");
    	String password = routingContext.request().headers().get("password");
    	String entidad = routingContext.request().headers().get("entidad");
    	
    	//User and password validation
    	if(usuario == null && password == null) {
    		this.getRoutingContextResponseByErrorCode(routingContext, new JsonObject(), "401", "");
    	} else if(password == null) {
    		this.getRoutingContextResponseByErrorCode(routingContext, new JsonObject(), "402", "");
    	} else if(usuario == null) {
    		this.getRoutingContextResponseByErrorCode(routingContext, new JsonObject(), "403", "");
    	} else if(entidad == null) {
    		this.getRoutingContextResponseByErrorCode(routingContext, new JsonObject(), "404", "");
    	} else if(!usuario.equalsIgnoreCase("USUARIO_AUTORIZADO") || !password.equalsIgnoreCase("12D1ERE5S4R5SR4WER4SD4S5DF4S5S5F4")){
    		this.getRoutingContextResponseByErrorCode(routingContext, new JsonObject(), "405", "");
    	} else {
    		//Allowing CORS - Cross Domain API calls
    		routingContext.response().putHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN,"*");
    		routingContext.response().putHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS,"GET,POST,PUT,DELETE");
    		routingContext.response().putHeader(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, "*");
    		
    		//Call the next matching route
        	routingContext.next();
    	}
    }
	
	/**
	 * Payment order searching by service code (payment order code) and customer code. Payment order information is deployed in
	 * JSON format and it includes the payment order amount, customer name, customer code, service code and all fees (if apply).
	 * The process to be executed is as follows:
	 * <ol>
	 * 	<li>Body validation</li>
	 * 	<li>CodServicio and CodigoBusqueda format validation</li>
	 * 	<li>Customer code searching in Roxxy database</li>
	 * 	<li>Payment order code searching in Neivor database</li>
	 * 	<li>Searching payment order details in Neivor database</li>
	 * 	<li>Searching payment order fees in Neivor database if aplies</li>
	 * </ol>
	 * @param routingContext Represents the context for the handling of a request in Vert.x-Web.
	 */
	public void searchPaymentsByServiceCode(RoutingContext routingContext) {
		
		JsonObject jsonBody;
		String codigoBusqueda = null;
		String codServicio = null;
		
		//Recovering fields from body request
		try {
			jsonBody = routingContext.getBodyAsJson();
			
			codigoBusqueda = jsonBody.getString("codigoBusqueda");
			codServicio = jsonBody.getString("codServicio");
		} catch (Exception e) {
			codigoBusqueda = null;
			codServicio = null;
		}
		
		//Response to be sent in routingContext.end()
		ConsultaResponse response = new ConsultaResponse();
		response.setCodigoBusqueda(codigoBusqueda);
		response.setCodigoServicio(codServicio);
		
		//CodServicio and CodigoBusqueda format validation
		if(codServicio==null || codigoBusqueda==null) {
			this.getRoutingContextResponseByErrorCode(routingContext, response, "502", "");
		} else if(codServicio.length()!=3){
			this.getRoutingContextResponseByErrorCode(routingContext, response, "503", "CodServicio");
		} else if(codigoBusqueda.length()>14) {
			this.getRoutingContextResponseByErrorCode(routingContext, response, "503", "CodigoBusqueda");
		} else {
			final String codigoOrden = codServicio;
			final String codigoBusquedaF = codigoBusqueda;
			
			MySQLPool roxxyClient = RoxxyVerticle.roxxyClient;
			MySQLPool neivorClient = RoxxyVerticle.neivorClient;
			
			//Customer code searching in Roxxy database
			roxxyClient
				.preparedQuery("SELECT nombre FROM roxxy_clientes WHERE codigo = ?")
				.execute(Tuple.of(codigoBusqueda), resCliente -> {
					if(resCliente.succeeded()) {
						if(resCliente.result()!=null && resCliente.result().size()>0) {
							
							//Payment order code searching in Neivor database
							neivorClient
								.preparedQuery("SELECT codigo FROM neivor_ordenes_pago WHERE codigo = ? AND pagado = 0")
								.execute(Tuple.of(codigoOrden), resCodigoOrdenPago -> {
									if(resCodigoOrdenPago.succeeded()) {
										if(resCodigoOrdenPago.result()!=null && resCodigoOrdenPago.result().size()>0) {
											
											//Searching payment order details in Neivor database
											neivorClient
												.preparedQuery("SELECT s.id, s.importe_adeudado FROM neivor_ordenes_pago_detalles opd " + 
														"	INNER JOIN neivor_servicios s ON s.id = opd.servicio " + 
														"WHERE opd.orden_pago = ?")
												.execute(Tuple.of(codigoOrden), resServicios -> {
													if(resServicios.succeeded()) {
														if(resServicios.result()!=null && resServicios.result().size()>0) {
															for(Row row : resServicios.result()) {
																int servicio = row.getInteger(0);
																double importe = row.getDouble(1);
																
																//Searching payment order fees in Neivor database if aplies
																neivorClient
																	.preparedQuery("SELECT numero_cuota, detalle_cuota, fecha_vencimiento, importe_cuota, importe_comision FROM neivor_servicios_cuotas WHERE servicio = ?")
																	.execute(Tuple.of(servicio), resServicioCuotas -> {
																		if(resServicioCuotas.succeeded()) {
																			response.setCodigoBusqueda(codigoBusquedaF);
																			response.setCodigoServicio(codigoOrden);
																			
																			for(Row cliente : resCliente.result()) {
																				response.setNombreCliente(cliente.getString(0));
																			}
																			if(resServicioCuotas.result()!=null && resServicioCuotas.result().size()>0) {
																				response.setImporteAdeudado(0.0);
																				List<Pago> pagos = new ArrayList<>();
																				for(Row cuota : resServicioCuotas.result()) {
																					Pago pago = new Pago();
																					pago.setNumeroCuota(cuota.getInteger(0));
																					pago.setDetalleCuota(cuota.getString(1));
																					pago.setFechaVencimiento(cuota.getString(2));
																					pago.setImporteCuota(cuota.getDouble(3));
																					pago.setImporteComision(cuota.getDouble(4));
																					pagos.add(pago);
																				}
																				response.setPagos(pagos);
																			} else {
																				response.setImporteAdeudado(importe);
																			}
																			this.getRoutingContextResponseByErrorCode(routingContext, response, "000", "");
																		} else {
																			this.getRoutingContextResponseByErrorCode(routingContext, response, "501", "servicios_cuotas");
																		}
																	});
															}
														} else {
															this.getRoutingContextResponseByErrorCode(routingContext, response, "201", "NO SE HAN ENCONTRADO SERVICIOS PARA LA ORDEN DE PAGO "+codigoOrden);
														}
													} else {
														this.getRoutingContextResponseByErrorCode(routingContext, response, "501", "servicios");
													}
												});
										} else {
											this.getRoutingContextResponseByErrorCode(routingContext, response, "201", "ORDEN DE PAGO DESCONOCIDA");
										}
									} else {
										this.getRoutingContextResponseByErrorCode(routingContext, response, "501", "ordenes_pago");
									}
								});
						} else {
							this.getRoutingContextResponseByErrorCode(routingContext, response, "201", "CÓDIGO DE DEPOSITANTE NO EXISTENTE");
						}
					} else {
						this.getRoutingContextResponseByErrorCode(routingContext, response, "501", "clientes");
					}
				});
			
		}
	}
	
	/**
	 * Save the payment in Roxxy database and update the payment order state in Neivor database.
	 * The process to be executed is as follows:
	 * <ol>
	 * 	<li>Body validation</li>
	 * 	<li>CodigoBusqueda and FechaPago format validation</li>
	 * 	<li>Customer code searching in Roxxy database</li>
	 * 	<li>Payment order code and amount searching in Neivor database</li>
	 * 	<li>Payment order state validation (if it is not paid)</li>
	 * 	<li>If montoTotal is the same as payment order amount</li>
	 * 	<li>Create payment constancy in Roxxy database</li>
	 * 	<li>Recover transaction id of the payment constancy from Roxxy database</li>
	 * 	<li>Create payment constancy details for fees in Roxxy database (if applies)</li>
	 * 	<li>Update payment order state to paid in Neivor database</li>
	 * </ol>
	 * @param routingContext Represents the context for the handling of a request in Vert.x-Web.
	 */
	public void makePaymentForCondominium(RoutingContext routingContext) {
		JsonObject jsonBody;
		String codigoBusqueda = null;
		String ordenPago = null;
		Double montoTotal = null;
		String fechaPago = null;
		
		jsonBody = routingContext.getBodyAsJson();
		
		//Recovering fields from body request
		try {
			codigoBusqueda = jsonBody.getString("codigoBusqueda");
			ordenPago = jsonBody.getString("ordenPago");
			montoTotal = jsonBody.getDouble("montoTotal");
			fechaPago = jsonBody.getString("fechaPago");
		} catch (Exception e) {
			codigoBusqueda = null;
			ordenPago = null;
			montoTotal = null;
			fechaPago = null;
		}
		
		//Response to be sent in contextRouting.end()
		JsonObject response = new JsonObject();
		
		//Body validation - CodigoBusqueda and FechaPago format validation
		if(jsonBody==null || codigoBusqueda==null || ordenPago==null || montoTotal==null || fechaPago==null) {
			this.getRoutingContextResponseByErrorCode(routingContext, response, "502", "");
		} else if(codigoBusqueda.length()>14){
			this.getRoutingContextResponseByErrorCode(routingContext, response, "503", "CodigoBusqueda");
		}  else if(fechaPago.length()>8){
			this.getRoutingContextResponseByErrorCode(routingContext, response, "503", "FechaPago");
		} else {
			final String ordenPagoF = ordenPago;
			final Double montoTotalF = montoTotal;
			final String fechaPagoF = fechaPago;
			final String codigoBusquedaF = codigoBusqueda;
			
			MySQLPool roxxyClient = RoxxyVerticle.roxxyClient;
			MySQLPool neivorClient = RoxxyVerticle.neivorClient;
			
			//Customer code searching in Roxxy database
			roxxyClient
				.preparedQuery("SELECT nombre FROM roxxy_clientes WHERE codigo = ?")
				.execute(Tuple.of(codigoBusqueda), resCliente -> {
					if(resCliente.succeeded()) {
						if(resCliente.result()!=null && resCliente.result().size()>0) {
							
							//Payment order code and amount searching in Neivor database
							neivorClient
							.preparedQuery("SELECT codigo, valor_pagar, pagado FROM neivor_ordenes_pago WHERE codigo = ?")
							.execute(Tuple.of(ordenPagoF), resOrdenPago -> {
								if(resOrdenPago.succeeded()) {
									if(resOrdenPago.result()!=null && resOrdenPago.result().size()>0) {
										double valorPagar = 0.0;
										boolean pagado = false;
										for(Row row : resOrdenPago.result()) {
											valorPagar = row.getDouble(1);
											pagado = row.getBoolean(2);
										}
										
										//Payment order state validation (if it is not paid)
										//If montoTotal is the same as payment order amount
										if(montoTotalF==valorPagar && !pagado) {
											String nombreFactura = jsonBody.getString("nombreFactura");
											String nit = jsonBody.getString("nit");
											String lugarPago = jsonBody.getString("lugarPago");
											
											if(nombreFactura.length()>40) {
												nombreFactura = nombreFactura.substring(0, 40);
											}
											if(nit.length()>8) {
												nit = nit.substring(0, 8);
											}
											if(lugarPago.length()>10) {
												lugarPago = lugarPago.substring(0, 10);
											}
											
											//Create payment constancy in Roxxy database
											roxxyClient
												.preparedQuery("INSERT INTO roxxy_pagos (fecha_pago, codigo_cliente, monto_total, nombre_factura, nit, lugar_pago, orden_pago) VALUES (?, ?, ?, ?, ?, ?, ?)")
												.execute(Tuple.of(fechaPagoF, codigoBusquedaF, montoTotalF, nombreFactura, nit, lugarPago, ordenPagoF), resInsertPago -> {
													JsonArray detallePago = jsonBody.getJsonArray("detallePago");
													if(detallePago.size()>0) {
														
														//Recover transaction id of the payment constancy from Roxxy database
														roxxyClient
															.preparedQuery("SELECT id_transaccion FROM roxxy_pagos WHERE orden_pago = ?")
															.execute(Tuple.of(ordenPagoF), resIdTransaccion -> {
																if(resIdTransaccion.succeeded()) {
																	long idTransaccion = 0L;
																	for(Row rowTransaccion : resIdTransaccion.result()) {
																		idTransaccion = rowTransaccion.getLong(0);
																	}
																	for(int i = 0; i<detallePago.size(); i++) {
																		
																		//Create payment constancy details for fees in Roxxy database (if applies)
																		Pago pago = Json.decodeValue(detallePago.getJsonObject(i).toString(), Pago.class);
																		roxxyClient
																			.preparedQuery("INSERT INTO roxxy_detalles_pagos (id_transaccion, numero_cuota, importe_cuota) VALUES (?, ?, ?)")
																			.execute(Tuple.of(idTransaccion, pago.getNumeroCuota(), pago.getImporteCuota()));
																	}
																} else {
																	this.getRoutingContextResponseByErrorCode(routingContext, response, "501", "pagos");
																}
																
															});
													}
													
													//Update payment order state to paid in Neivor database
													neivorClient
														.preparedQuery("UPDATE neivor_ordenes_pago SET pagado = true WHERE codigo = ?")
														.execute(Tuple.of(ordenPagoF), resUpdatePago -> {
															if(resUpdatePago.succeeded()) {
																this.getRoutingContextResponseByErrorCode(routingContext, response, "000", "");
															} else {
																this.getRoutingContextResponseByErrorCode(routingContext, response, "501", "ordenes_pago");
															}
														});
												});
										} else if(pagado){
											this.getRoutingContextResponseByErrorCode(routingContext, response, "201", "ESTA ORDEN YA SE ENCUENTRA PAGADA");
										} else {
											this.getRoutingContextResponseByErrorCode(routingContext, response, "201", "EL MONTO TOTAL DIFIERE DEL VALOR A PAGAR");
										}
									} else {
										this.getRoutingContextResponseByErrorCode(routingContext, response, "201", "ORDEN DE PAGO DESCONOCIDA");
									}
								} else {
									this.getRoutingContextResponseByErrorCode(routingContext, response, "501", "ordenes_pago");
								}
							});
						} else {
							this.getRoutingContextResponseByErrorCode(routingContext, response, "201", "CÓDIGO DE DEPOSITANTE NO EXISTENTE");
						}
					} else {
						this.getRoutingContextResponseByErrorCode(routingContext, response, "501", "clientes");
					}
				});
		}
	}
	
	/**
	 * Revert payments according to an payment id. Deletes the payment in Roxxy database and update payment order state to false in Neivor database.
	 * The process to be executed is as follows:
	 * <ol>
	 * 	<li>Body validation</li>
	 * 	<li>CodigoBusqueda and FechaReversion format validation</li>
	 * 	<li>Reversion id validation (unique) in Roxxy database</li>
	 * 	<li>Payment id validation (if exists) in Roxxy database</li>
	 * 	<li>Customer code searching in Roxxy database</li>
	 * 	<li>Create reversion in Neivor database</li>
	 * 	<li>Recover reversion id from Neivor database</li>
	 * 	<li>Create reversion with reversion id from Neivor in Roxxy database</li>
	 * 	<li>Update payment order state to false in Neivor database</li>
	 * 	<li>Delete payment and payment fees in Roxxy database</li>
	 * </ol>
	 * @param routingContext Represents the context for the handling of a request in Vert.x-Web.
	 */
	public void revertPaymentForCondominium(RoutingContext routingContext) {
		JsonObject jsonBody;
		String codigoBusqueda = null;
		Long idPago = null;
		Long idReversion = null;
		String fechaReversion = null;
		
		jsonBody = routingContext.getBodyAsJson();
		
		//Recovering fields from body request
		try {
			codigoBusqueda = jsonBody.getString("codigoBusqueda");
			idPago = jsonBody.getLong("idPago");
			idReversion = jsonBody.getLong("idReversion");
			fechaReversion = jsonBody.getString("fechaReversion");
		} catch (Exception e) {
			codigoBusqueda = null;
			idPago = null;
			idReversion = null;
			fechaReversion = null;
		}
		
		//Response to be sent int contextRouting.end()
		ReversionResponse response = new ReversionResponse();
		response.setCodigoBusqueda(codigoBusqueda);
		response.setIdTxnRevertida(idReversion);
		
		//Body validation - CodigoBusqueda and FechaReversion format validation
		if(jsonBody==null || codigoBusqueda==null || idPago==null || idReversion==null || fechaReversion==null) {
			this.getRoutingContextResponseByErrorCode(routingContext, response, "502", "");
		} else if(codigoBusqueda.length()>14){
			this.getRoutingContextResponseByErrorCode(routingContext, response, "503", "CodigoBusqueda");
		}  else if(fechaReversion.length()!=8){
			this.getRoutingContextResponseByErrorCode(routingContext, response, "503", "fechaReversion");
		} else {
			final Long idPagoF = idPago;
			final Long idReversionF = idReversion;
			final String codigoBusquedaF = codigoBusqueda;
			final String fechaReversionF = fechaReversion;
			
			MySQLPool roxxyClient = RoxxyVerticle.roxxyClient;
			MySQLPool neivorClient = RoxxyVerticle.neivorClient;
			
			//Reversion id validation (unique) in Roxxy database
			roxxyClient
				.preparedQuery("SELECT id_reversion FROM roxxy_reversiones WHERE id_reversion = ?")
				.execute(Tuple.of(idReversionF), resReversion -> {
					if(resReversion.succeeded()) {
						if(resReversion.result()!=null && resReversion.result().size()>0) {
							this.getRoutingContextResponseByErrorCode(routingContext, response, "201", "EL ID DE LA REVERSION DEBE SER UNICO");
						} else {
							
							//Payment id validation (if exists) in Roxxy database
							roxxyClient
								.preparedQuery("SELECT id_transaccion, monto_total, orden_pago FROM roxxy_pagos WHERE id_transaccion = ?")
								.execute(Tuple.of(idPagoF), resPago -> {
									if(resPago.succeeded()) {
										if(resPago.result()!=null && resPago.result().size()>0) {
											
											//Customer code searching in Roxxy database
											roxxyClient
												.preparedQuery("SELECT nombre FROM roxxy_clientes WHERE codigo = ?")
												.execute(Tuple.of(codigoBusquedaF), resCliente -> {
													if(resCliente.succeeded()) {
														if(resCliente.result()!=null && resCliente.result().size()>0) {
															double montoRevertido = 0.0;
															String ordenPago = "";
															for(Row rowPago : resPago.result()) {
																montoRevertido = rowPago.getDouble(1);
																ordenPago = rowPago.getString(2);
															}
															final double montoRevertidoF = montoRevertido;
															final String ordenPagoF = ordenPago;
															
															//Create reversion in Neivor database
															neivorClient
																.preparedQuery("INSERT INTO neivor_reversiones (fecha_reversion, orden_pago, monto_revertido) VALUES (?, ?, ?)")
																.execute(Tuple.of(fechaReversionF, ordenPago, montoRevertido), resInsertReversionNeivor -> {
																	if(resInsertReversionNeivor.succeeded()) {
																		
																		//Recover reversion id from Neivor database
																		neivorClient
																			.preparedQuery("SELECT id_reversion FROM neivor_reversiones WHERE orden_pago = ?")
																			.execute(Tuple.of(ordenPagoF), resReversionNeivor -> {
																				if(resReversionNeivor.succeeded()) {
																					long idReversionNeivor = 0L;
																					for(Row rowReversion : resReversionNeivor.result()) {
																						idReversionNeivor = rowReversion.getLong(0);
																					}
																					if(resReversionNeivor.result()!=null && resReversionNeivor.result().size()>0) {
																						
																						//Create reversion with reversion id from Neivor in Roxxy database
																						roxxyClient
																							.preparedQuery("INSERT INTO roxxy_reversiones (id_reversion, fecha_reversion, monto_revertido, id_txn_neivor_reversion, codigo_cliente) VALUES (?, ?, ?, ?, ?)")
																							.execute(Tuple.of(idReversionF, fechaReversionF, montoRevertidoF, idReversionNeivor, codigoBusquedaF), resInsertReversionRoxxy -> {
																								if(resInsertReversionRoxxy.succeeded()) {
																									response.setIdTxnNeivorResponse(idReversionF);
																									
																									
																									//Update payment order state to false in Neivor database
																									neivorClient
																										.preparedQuery("UPDATE neivor_ordenes_pago SET pagado = false WHERE codigo = ?")
																										.execute(Tuple.of(ordenPagoF), resUpdateOrdenPagoNeivor -> {
																											if(resUpdateOrdenPagoNeivor.succeeded()) {
																												
																												//Delete payment and payment fees in Roxxy database
																												roxxyClient
																													.preparedQuery("DELETE FROM roxxy_detalles_pagos WHERE id_transaccion = ?")
																													.execute(Tuple.of(idPagoF), resDeleteDetallePagoRoxxy -> {
																														if(resDeleteDetallePagoRoxxy.succeeded()) {
																															roxxyClient
																																.preparedQuery("DELETE FROM roxxy_pagos WHERE id_transaccion = ?")
																																.execute(Tuple.of(idPagoF), resDeletePagoRoxxy -> {
																																	if(resDeletePagoRoxxy.succeeded()) {
																																		this.getRoutingContextResponseByErrorCode(routingContext, response, "000", "");
																																	} else {
																																		this.getRoutingContextResponseByErrorCode(routingContext, response, "201", "NO SE HA PODIDO BORRAR EL PAGO EN ROXXY");
																																	}
																																});
																														} else {
																															this.getRoutingContextResponseByErrorCode(routingContext, response, "201", "NO SE HA PODIDO BORRAR LOS DETALLES DEL PAGO EN ROXXY");
																														}
																													});
																											} else {
																												this.getRoutingContextResponseByErrorCode(routingContext, response, "201", "NO SE HA LOGRADO ACTUALIZAR LA ORDEN DE PAGO EN NEIVOR");
																											}
																										});
																								} else {
																									this.getRoutingContextResponseByErrorCode(routingContext, response, "201", "NO SE HA LOGRADO CREAR LA REVERSION EN ROXXY");
																								}
																							});
																					} else {
																						this.getRoutingContextResponseByErrorCode(routingContext, response, "201", "NO SE HA LOGRADO OBTENER EL ID REVERSION DE NEIVOR");
																					}
																				} else {
																					this.getRoutingContextResponseByErrorCode(routingContext, response, "501", "reversiones");
																				}
																			});
																	} else {
																		this.getRoutingContextResponseByErrorCode(routingContext, response, "201", "NO SE HA LOGRADO CREAR LA REVERSION EN NEIVOR");
																	}
																});
														} else {
															this.getRoutingContextResponseByErrorCode(routingContext, response, "201", "CÓDIGO DE DEPOSITANTE NO EXISTENTE");
														}
													} else {
														this.getRoutingContextResponseByErrorCode(routingContext, response, "501", "clientes");
													}
												});
										} else {
											this.getRoutingContextResponseByErrorCode(routingContext, response, "201", "ID DE PAGO DESCONOCIDO");
										}
									} else {
										this.getRoutingContextResponseByErrorCode(routingContext, response, "501", "pagos");
									}
								});
						}
					} else {
						this.getRoutingContextResponseByErrorCode(routingContext, response, "501", "reversiones");
					}
				});
		}
	}
	
	/**
	 * Creates routing context response according to codError parameter. Puts the following headers:
	 * <ul>
	 * 	<li>content-type: application/json</li>
	 * 	<li>codError: see error codes list below</li>
	 * 	<li>descripcion: error description</li>
	 * </ul>
	 * @param routingContext Represents the context for the handling of a request in Vert.x-Web. 
	 * @param object Load to be enconded prettily by Json for routing context
	 * @param codError Custom error code for neivor. Can take the following values:
	 * <ul>
	 * 	<li><b>000</b>: PROCESO CONFORME (status code 200)</li>
	 * 	<li><b>201</b>: message (status code 404)</li>
	 * 	<li><b>401</b>: USUARIO Y CONTRASEÑA NO PROPORCIONADOS (status code 401)</li>
	 * 	<li><b>402</b>: CONTRASEÑA NO PROPORCIONADA (status code 401)</li>
	 * 	<li><b>403</b>: USUARIO NO PROPORCIONADO (status code 401)</li>
	 * 	<li><b>404</b>: ENTIDAD NO PROPORCIONADA (status code 401)</li>
	 * 	<li><b>405</b>: CREDENCIALES INVALIDAS (status code 401)</li>
	 * 	<li><b>501</b>: PROBLEMAS CON LA CONEXION + message (status code 404)</li>
	 * 	<li><b>502</b>: CARGA UTIL NO VALIDA (status code 400)</li>
	 * 	<li><b>503</b>: FORMATO NO VALIDO PARA + message (status code 400)</li>
	 * </ul>
	 * @param message Custom extra message for descripcion header
	 */
	public void getRoutingContextResponseByErrorCode(RoutingContext routingContext, Object object, String codError, String message) {
		switch(codError) {
			case "000":
				routingContext.response()
					.setStatusCode(200)
					.putHeader("content-type", "application/json")
					.putHeader("codError", codError)
					.putHeader("descripcion", "PROCESO CONFORME")
					.end(Json.encodePrettily(object));
				break;
			case "201":
				routingContext.response()
					.setStatusCode(404)
					.putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
					.putHeader("codError", codError)
					.putHeader("descripcion", message)
					.end(Json.encodePrettily(object));
				break;
			case "401":
				routingContext.response()
					.setStatusCode(401)
					.putHeader("content-type", "application/json")
					.putHeader("codError", codError)
					.putHeader("descripcion", "USUARIO Y CONTRASEÑA NO PROPORCIONADOS")
					.end(Json.encodePrettily(object));
				break;
			case "402":
				routingContext.response()
					.setStatusCode(401)
					.putHeader("content-type", "application/json")
					.putHeader("codError", codError)
					.putHeader("descripcion", "CONTRASEÑA NO PROPORCIONADA")
					.end(Json.encodePrettily(object));
				break;
			case "403":
				routingContext.response()
					.setStatusCode(401)
					.putHeader("content-type", "application/json")
					.putHeader("codError", codError)
					.putHeader("descripcion", "USUARIO NO PROPORCIONADO")
					.end(Json.encodePrettily(object));
				break;
			case "404":
				routingContext.response()
					.setStatusCode(401)
					.putHeader("content-type", "application/json")
					.putHeader("codError", codError)
					.putHeader("descripcion", "ENTIDAD NO PROPORCIONADA")
					.end(Json.encodePrettily(object));
				break;
			case "405":
				routingContext.response()
					.setStatusCode(401)
					.putHeader("content-type", "application/json")
					.putHeader("codError", codError)
					.putHeader("descripcion", "CREDENCIALES INVALIDAS")
					.end(Json.encodePrettily(object));
				break;
			case "501":
				routingContext.response()
					.setStatusCode(404)
					.putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
					.putHeader("codError", codError)
					.putHeader("descripcion", "PROBLEMAS CON LA CONEXION: "+message)
					.end(Json.encodePrettily(object));
				break;
			case "502":
				routingContext.response()
					.setStatusCode(400)
					.putHeader("content-type", "application/json")
					.putHeader("codError", codError)
					.putHeader("descripcion", "CARGA UTIL NO VALIDA")
					.end(Json.encodePrettily(object));
				break;
			case "503":
				routingContext.response()
					.setStatusCode(400)
					.putHeader("content-type", "application/json")
					.putHeader("codError", codError)
					.putHeader("descripcion", "FORMATO NO VALIDO PARA "+message)
					.end(Json.encodePrettily(object));
				break;
		}
	}
}
