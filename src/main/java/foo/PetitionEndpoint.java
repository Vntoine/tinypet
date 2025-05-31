package foo;

import java.util.*;
import java.util.stream.Collectors;

import com.google.api.server.spi.auth.common.User;
import com.google.api.server.spi.config.Api;
import com.google.api.server.spi.config.ApiMethod;
import com.google.api.server.spi.config.ApiMethod.HttpMethod;
import com.google.api.server.spi.config.ApiNamespace;
import com.google.api.server.spi.config.Named;
import com.google.api.server.spi.config.Nullable;					
import com.google.api.server.spi.response.CollectionResponse;
import com.google.api.server.spi.response.UnauthorizedException;
import com.google.api.server.spi.response.NotFoundException;

import com.google.appengine.api.datastore.Cursor;
import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.EntityNotFoundException;
import com.google.appengine.api.datastore.FetchOptions;
import com.google.appengine.api.datastore.Query;
import com.google.appengine.api.datastore.PreparedQuery;
import com.google.appengine.api.datastore.Query.FilterOperator;
import com.google.appengine.api.datastore.Query.FilterPredicate;
import com.google.appengine.api.datastore.Query.SortDirection;
import com.google.appengine.api.datastore.QueryResultList;
import com.google.appengine.api.datastore.Transaction;
import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.datastore.KeyFactory;

@Api(name = "myApi",
     version = "v1",
     audiences = {"1090580414141-a27topbs048b1m3sok3675h7cnn3o3i4.apps.googleusercontent.com"},
  	 clientIds = {"1090580414141-a27topbs048b1m3sok3675h7cnn3o3i4.apps.googleusercontent.com"},

     namespace =
     @ApiNamespace(
		   ownerDomain = "helloworld.example.com",
		   ownerName = "helloworld.example.com",
		   packagePath = "")
     )
	 
public class PetitionEndpoint {

	Random r = new Random();

	@ApiMethod(name = "hello", httpMethod = HttpMethod.GET)
	public User Hello(User user) throws UnauthorizedException {
        if (user == null) {
			throw new UnauthorizedException("Invalid credentials");
		}
        System.out.println("Yeah:"+user.toString());
		return user;
	}

	@ApiMethod(name = "addPetition", httpMethod = HttpMethod.POST)
	public Entity addPetition(User user, Petition petition) throws UnauthorizedException {
		if (user == null) {
			throw new UnauthorizedException("Invalid credentials");
		}
		String email = user.getEmail();
		DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
		Transaction txn = datastore.beginTransaction();
		
		try {
			Set<String> tags = new HashSet<>();
			if(petition.tags != null){
				String[] splitTags = petition.tags.contains(",") ? petition.tags.split(",") : petition.tags.split(" ");
				for (String tag : splitTags) {
					if (!tag.trim().isEmpty()) {
						tags.add(tag.trim().toLowerCase());
					}
				}
			}

			Entity p = new Entity("Petition");
			p.setProperty("auteur", email);
			p.setProperty("titre", petition.titre);
			p.setProperty("description", petition.description);
			p.setProperty("dateCreation", new Date());
			p.setProperty("tags", tags);
			p.setProperty("nbSignatures", 0);

			datastore.put(txn, p);
			txn.commit();
			return p;
		} catch (Exception e) {
        	if (txn.isActive()) {
            	txn.rollback();
        	}
       	 	throw e;
    	}
	}

	@ApiMethod(name = "addSignature", httpMethod = HttpMethod.POST)
	public Entity addSignature(User user, Signature signature) throws UnauthorizedException, EntityNotFoundException {
		if (user == null) {
			throw new UnauthorizedException("Invalid credentials");
		}

		DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
		Query.Filter emailFilter = new Query.FilterPredicate("email", Query.FilterOperator.EQUAL, signature.email);
		Query.Filter pidFilter = new Query.FilterPredicate("pid", Query.FilterOperator.EQUAL, signature.pid);
		Query.Filter compositeFilter = Query.CompositeFilterOperator.and(emailFilter, pidFilter);
		Query q = new Query("Signature").setFilter(compositeFilter);
		PreparedQuery pq = datastore.prepare(q);
		Entity signatureEntity = pq.asSingleEntity();
		
		if (signatureEntity != null) {
			throw new IllegalArgumentException("Vous avez déjà signé cette pétition");
		}

		Transaction txn = datastore.beginTransaction();
		try {
			signatureEntity = new Entity("Signature");
			signatureEntity.setProperty("email", signature.email);
			signatureEntity.setProperty("pid", signature.pid);
			datastore.put(txn, signatureEntity);

        	long pid = Long.parseLong(signature.pid);
			Key petitionKey = KeyFactory.createKey("Petition", pid);
        	Entity petitionEntity = datastore.get(petitionKey);
        	long nbSignatures = (long) petitionEntity.getProperty("nbSignatures");
        	petitionEntity.setProperty("nbSignatures", nbSignatures + 1);
        	
			datastore.put(txn, petitionEntity);
        	txn.commit();

        	return signatureEntity;
    	} catch (EntityNotFoundException e) {
			throw new IllegalArgumentException("La pétition avec cet ID n'existe pas");
		} catch (Exception e) {
			throw e;
		} finally {
			if (txn.isActive()) {
				txn.rollback();
			}
		}
	}

	@ApiMethod(name = "topPetitions", httpMethod = HttpMethod.GET)
	public CollectionResponse<Entity> topPetitions(@Nullable @Named("next") String cursorString) {
		DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
		Query q = new Query("Petition").addSort("dateCreation", SortDirection.DESCENDING);
		
		FetchOptions fetchOptions = FetchOptions.Builder.withLimit(10);

		if (cursorString != null) {
			fetchOptions.startCursor(Cursor.fromWebSafeString(cursorString));
		}

		PreparedQuery pq = datastore.prepare(q);
		QueryResultList<Entity> results = pq.asQueryResultList(fetchOptions);
		cursorString = results.getCursor().toWebSafeString();
		
		return CollectionResponse.<Entity>builder().setItems(results).setNextPageToken(cursorString).build();
	}

	@ApiMethod(name="tagPetitions", httpMethod=HttpMethod.GET)
	public CollectionResponse<Entity> tagPetitions(@Named("tag") String tag, @Nullable @Named("next") String cursorString) throws Exception {
		DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
		List<String> tagList = List.of(tag.trim().toLowerCase());
		Query q = new Query("Petition")
			.setFilter(new FilterPredicate("tags", FilterOperator.IN, tagList))
			.addSort("dateCreation", SortDirection.DESCENDING);
		FetchOptions fetchOptions = FetchOptions.Builder.withLimit(10);

		if (cursorString != null) {
			fetchOptions.startCursor(Cursor.fromWebSafeString(cursorString));
		}

		PreparedQuery pq = datastore.prepare(q);
		QueryResultList<Entity> results = pq.asQueryResultList(fetchOptions);
		cursorString = results.getCursor().toWebSafeString();
		
		return CollectionResponse.<Entity>builder().setItems(results).setNextPageToken(cursorString).build();
	}

	@ApiMethod(name="signatairesPetition", httpMethod=HttpMethod.GET)
	public CollectionResponse<Entity> signatairesPetition(@Named("pid") String pid, @Nullable @Named("next") String cursorString) throws Exception {
		DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
		Query.Filter pidFilter = new Query.FilterPredicate("pid", Query.FilterOperator.EQUAL, pid);
    	Query q = new Query("Signature").setFilter(pidFilter);
		
		FetchOptions fetchOptions = FetchOptions.Builder.withLimit(10);
		if (cursorString != null) {
			fetchOptions.startCursor(Cursor.fromWebSafeString(cursorString));
		}

		PreparedQuery pq = datastore.prepare(q);
		QueryResultList<Entity> results = pq.asQueryResultList(fetchOptions);
		cursorString = results.getCursor().toWebSafeString();

		return CollectionResponse.<Entity>builder().setItems(results).setNextPageToken(cursorString).build();
	}

	@ApiMethod(name="signedByPetitions", httpMethod=HttpMethod.GET)
	public CollectionResponse<Entity> signedByPetitions(@Named("email") String email, @Nullable @Named("next") String cursorString) throws Exception {
		DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
		Query q = new Query("Signature").setFilter(new FilterPredicate("email", FilterOperator.EQUAL, email));

		FetchOptions fetchOptions = FetchOptions.Builder.withLimit(10);
		if (cursorString != null) {
			fetchOptions.startCursor(Cursor.fromWebSafeString(cursorString));
		}

		PreparedQuery pq = datastore.prepare(q);
		QueryResultList<Entity> signatures = pq.asQueryResultList(fetchOptions);
		cursorString = signatures.getCursor().toWebSafeString();

		if (signatures.isEmpty()) {
			return CollectionResponse.<Entity>builder()
				.setItems(Collections.emptyList())
				.setNextPageToken(null)
				.build();
		}

		List<Key> petitionIds = new ArrayList<>();
		for (Entity signature : signatures) {
			long pid = Long.parseLong(signature.getProperty("pid").toString());
			petitionIds.add(KeyFactory.createKey("Petition", pid));
		}

		Map<Key, Entity> petitionEntitiesMap = datastore.get(petitionIds);

		return CollectionResponse.<Entity>builder()
			.setItems(new ArrayList<>(petitionEntitiesMap.values()))
			.setNextPageToken(cursorString)
			.build();
	}
}
