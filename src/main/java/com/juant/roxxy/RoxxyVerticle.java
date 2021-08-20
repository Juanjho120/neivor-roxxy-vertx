package com.juant.roxxy;

import com.juant.roxxy.handler.NeivorHandler;
import com.juant.roxxy.handler.RoxxyHandler;

import io.vertx.config.ConfigRetriever;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.StaticHandler;
import io.vertx.mysqlclient.MySQLConnectOptions;
import io.vertx.mysqlclient.MySQLPool;
import io.vertx.sqlclient.PoolOptions;

/**
 * Component designed to run inside Vertx. Contains web service settings for Roxxy stores
 * and payment generation for Neivor
 * @author Juan Tzun
 *
 */
public class RoxxyVerticle extends AbstractVerticle {
	
	//MySQL client connections
	public static MySQLPool roxxyClient;
	public static MySQLPool neivorClient;
		
    public static void main( String[] args ) {
    	Vertx vertx = Vertx.vertx();
        
    	// Use config/config.json from resources/classpath
    	ConfigRetriever configRetriever = ConfigRetriever.create(vertx);
    	
    	configRetriever.getConfig(config -> {
    		if(config.succeeded()) {
    			JsonObject configJson = config.result();
    			
    			DeploymentOptions options = new DeploymentOptions().setConfig(configJson);
    			
    			vertx.deployVerticle(new RoxxyVerticle(), options);
    		}
    	});
    }
    
    @Override
    public void start() {
    	System.out.println("Verticle RoxxyVerticle Started");
    	
    	Router router = Router.router(vertx);
    	
    	//Connection options for roxxy database
		MySQLConnectOptions roxxyConnectOptions = new MySQLConnectOptions()
				  .setPort(config().getInteger("database.port"))
				  .setHost(config().getString("database.host"))
				  .setDatabase(config().getString("roxxy.database.name"))
				  .setUser(config().getString("database.user"))
				  .setPassword(config().getString("database.password"));
		
		//Connection options for neivor database
		MySQLConnectOptions neivorConnectOptions = new MySQLConnectOptions()
				  .setPort(config().getInteger("database.port"))
				  .setHost(config().getString("database.host"))
				  .setDatabase(config().getString("neivor.database.name"))
				  .setUser(config().getString("database.user"))
				  .setPassword(config().getString("database.password"));
		
		// Pool options
		PoolOptions poolOptions = new PoolOptions()
				.setMaxSize(5);
				
		// Create the pooled client
		roxxyClient = MySQLPool.pool(vertx, roxxyConnectOptions, poolOptions);
		neivorClient = MySQLPool.pool(vertx, neivorConnectOptions, poolOptions);
    			
    	//Initiate handlers for APIs sub routes
    	RoxxyHandler roxxyHandler = new RoxxyHandler();
    	NeivorHandler neivorHandler = new NeivorHandler();
    	
    	router.mountSubRouter("/api/neivor/", neivorHandler.getAPISubRouter(vertx));
    	router.mountSubRouter("/api/roxxy/", roxxyHandler.getAPISubRouter(vertx));
    	
    	//Default if no routes are matched
    	router.route().handler(StaticHandler.create().setCachingEnabled(false));
    	
    	//Configure listen port -> http.port
    	vertx.createHttpServer().requestHandler(router).listen(config().getInteger("http.port"), asyncResult -> {
    		//If port is not occupied
    		if(asyncResult.succeeded()) {
    			System.out.println("HTTP server running on port "+config().getInteger("http.port"));
    		} else {
    			System.out.println("Could not start a HTTP server");
    		}
    	});
    }
    
    @Override
    public void stop() {
    	System.out.println("Verticle RoxxyVerticle Stopped");
    }
}
