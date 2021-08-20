package com.juant.roxxy.handler;

import com.juant.roxxy.RoxxyVerticle;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.mysqlclient.MySQLPool;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.Tuple;

/**
 * Handles API routing for Neivor
 * @author Juan Tzun
 *
 */
public class NeivorHandler {
	
	/**
	 * Creates configuration for database connection and defines all available routes starting by /api/neivor.
	 * The following APIs are available:
	 * <ul>
	 * 	<li>POST /api/neivor/generate-payment-order (see generatePaymentOrder)</li>
	 * 	<li>GET /api/neivor/payment-order-state/:code (see getPaymentOrderStateByCode)</li>
	 * </ul>
	 * @param vertx The entry point into the Vert.x Core API. 
	 * @return Router with Neivor routes
	 */
	public Router getAPISubRouter(Vertx vertx) {
		
		Router apiSubRouter = Router.router(vertx);
		
		// API Routing
		apiSubRouter.route("/*").handler(this::defaultProcessorForNeivorAPI);
    	apiSubRouter.route("/*").handler(BodyHandler.create());
    	apiSubRouter.post("/generate-payment-order").handler(this::generatePaymentOrder);
    	apiSubRouter.get("/payment-order-state/:code").handler(this::getPaymentOrderStateByCode);
    	
		return apiSubRouter;
	}
	
	/**
	 * Called for all default API HTTP GET, POST, PUT and DELETE. Enables cross origin
	 * @param routingContext Represents the context for the handling of a request in Vert.x-Web.
	 */
	public void defaultProcessorForNeivorAPI(RoutingContext routingContext) {
		//Allowing CORS - Cross Domain API calls
		routingContext.response().putHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN,"*");
		routingContext.response().putHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS,"GET,POST,PUT,DELETE");
		routingContext.response().putHeader(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, "*");
		
		//Call the next matching route
    	routingContext.next();
	}
	
	/**
	 * Payment order generation.
	 * The process to be executed is as follows:
	 * <ol>
	 * 	<li>Body validation</li>
	 * 	<li>Resident payable services validation</li>
	 * 	<li>Resident payable services sum calculation</li>
	 * 	<li>Payment order code generation</li>
	 * 	<li>Payment order creation in Neivor database with details. Details associates services with payment order</li>
	 * </ol>
	 * @param routingContext Represents the context for the handling of a request in Vert.x-Web.
	 */
	public void generatePaymentOrder(RoutingContext routingContext) {
		JsonObject jsonBody;
		
		jsonBody = routingContext.getBodyAsJson();
		
		JsonObject response = new JsonObject().put("orden", "");
		
		//Body validation
		if(jsonBody==null) {
			this.getRoutingContextResponseByErrorCode(routingContext, response, "502", "");
		} else {
			
			//Recovering fields from headers request
			String nombrePagador = jsonBody.getString("nombrePagador");
			String documentoPagador = jsonBody.getString("documentoPagador");
			String numeroDepartamento = jsonBody.getString("numeroDepartamento");
			
			MySQLPool client = RoxxyVerticle.neivorClient;
			
			//Resident payable services validation
			client
				.preparedQuery("SELECT id FROM neivor_servicios WHERE id NOT IN (SELECT servicio FROM neivor_ordenes_pago_detalles) AND departamento = ?")
				.execute(Tuple.of(numeroDepartamento), resServicios -> {
					if(resServicios.succeeded()) {
						if(resServicios.result()!=null && resServicios.result().size()>0) {
							
							//Resident payable services sum calculation
							client
							.preparedQuery("SELECT COALESCE(SUM(importe_adeudado),0) FROM neivor_servicios WHERE id NOT IN (SELECT servicio FROM neivor_ordenes_pago_detalles) AND departamento = ?")
							.execute(Tuple.of(numeroDepartamento), resImporte -> {
								if(resImporte.succeeded() ) {
									
									//Payment order code generation
									client
										.preparedQuery("SELECT COUNT(*) FROM neivor_ordenes_pago")
										.execute(resCuenta -> {
											if(resCuenta.succeeded()) {
												double importe = 0.0;
												for(Row row : resImporte.result()) {
													importe = row.getDouble(0);
												}
												
												Integer cuenta = 0;
												for(Row row : resCuenta.result()) {
													cuenta = row.getInteger(0);
												}
												String codigo = "";
												for(int i = 0; i<(3-cuenta.toString().length()); i++) {
													codigo += "0";
												}
												codigo += ++cuenta;
												
												final String ordenPago = codigo;
												
												//Payment order creation in Neivor database with details. Details associates services with payment order
												client
													.preparedQuery("INSERT INTO neivor_ordenes_pago (codigo, nombre_pagador, documento_pagador, numero_departamento, valor_pagar, pagado) VALUES (?, ?, ?, ?, ?, ?)")
													.execute(Tuple.of(ordenPago, nombrePagador, documentoPagador, numeroDepartamento, importe, 0))
													.onComplete(resInsertOrdenPago -> {
														for(Row row : resServicios.result()) {
															client
																.preparedQuery("INSERT INTO neivor_ordenes_pago_detalles (orden_pago, servicio) VALUES (?, ?)")
																.execute(Tuple.of(ordenPago, row.getInteger(0)));
														}
														this.getRoutingContextResponseByErrorCode(routingContext, new JsonObject().put("orden", ordenPago), "000", "");
													});
											} else {
												this.getRoutingContextResponseByErrorCode(routingContext, response, "201", "NO SE HA PODIDO CREAR EL CODIGO DE LA ORDEN DE PAGO");
											}
										});
								} else {
									this.getRoutingContextResponseByErrorCode(routingContext, response, "201", "NO SE HA PODIDO RECUPERAR EL VALOR A PAGAR");
								}
							});
						} else {
							this.getRoutingContextResponseByErrorCode(routingContext, response, "101", "");
						}
					} else {
						this.getRoutingContextResponseByErrorCode(routingContext, response, "501", "servicios");
					}
				});
		}
	}
	
	/**
	 * See payment order state by code if exist.
	 * @param routingContext Represents the context for the handling of a request in Vert.x-Web.
	 */
	public void getPaymentOrderStateByCode(RoutingContext routingContext) {
		//Recovering code parameter from request
		String codigo = routingContext.request().getParam("code");
		
		//Response to be sent in contextRouting.end()
		JsonObject response = new JsonObject().put("ordenEstado", "");
		
		if(codigo==null) {
			this.getRoutingContextResponseByErrorCode(routingContext, response, "201", "CODIGO DE ORDEN NO PROPORCIONADO");
		} else {
			
			MySQLPool client = RoxxyVerticle.neivorClient;
			
			//Search payment order state by code in Neivor database
			client
				.preparedQuery("SELECT pagado FROM neivor_ordenes_pago WHERE codigo = ?")
				.execute(Tuple.of(codigo), resOrdenPago -> {
					if(resOrdenPago.succeeded()) {
						if(resOrdenPago.result()!=null && resOrdenPago.result().size()>0) {
							Boolean pagado = null;
							for(Row row : resOrdenPago.result()) {
								pagado = row.getBoolean(0);
							}
							JsonObject responseF = new JsonObject().put("ordenEstado", pagado);
							this.getRoutingContextResponseByErrorCode(routingContext, responseF, "000", "");
						} else {
							this.getRoutingContextResponseByErrorCode(routingContext, response, "201", "ORDEN DE PAGO DESCONOCIDA");
						}
					} else {
						this.getRoutingContextResponseByErrorCode(routingContext, response, "501", "ordenes_pago");
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
	 * 	<li><b>101</b>: NO HAY SERVICIOS PARA PROCESAR ORDEN DE PAGO (status code 404)</li>
	 * 	<li><b>201</b>: message (status code 404)</li>
	 * 	<li><b>501</b>: PROBLEMAS CON LA CONEXION + message (status code 404)</li>
	 * 	<li><b>502</b>: CARGA UTIL NO VALIDA (status code 400)</li>
	 * </ul>
	 * @param message Custom extra message for descripcion header
	 */
	public void getRoutingContextResponseByErrorCode(RoutingContext routingContext, Object object, String codError, String message) {
		switch(codError) {
			case "000":
				routingContext.response()
					.setStatusCode(200)
					.putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
					.putHeader("codError", codError)
					.putHeader("descripcion", "PROCESO CONFORME")
					.end(Json.encodePrettily(object));
				break;
			case "101":
				routingContext.response()
					.setStatusCode(200)
					.putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
					.putHeader("codError", codError)
					.putHeader("descripcion", "NO HAY SERVICIOS PARA PROCESAR ORDEN DE PAGO")
					.end(Json.encodePrettily(object));
				break;
			case "201":
				routingContext.response()
					.setStatusCode(200)
					.putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
					.putHeader("codError", codError)
					.putHeader("descripcion", message)
					.end(Json.encodePrettily(object));
				break;
			case "501":
				routingContext.response()
					.setStatusCode(200)
					.putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
					.putHeader("codError", codError)
					.putHeader("descripcion", "PROBLEMAS CON LA CONEXION: "+message)
					.end(Json.encodePrettily(object));
				break;
			case "502":
				routingContext.response()
					.setStatusCode(200)
					.putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
					.putHeader("codError", codError)
					.putHeader("descripcion", "CARGA UTIL NO VALIDA")
					.end(Json.encodePrettily(object));
				break;
		}
	}
	
}
