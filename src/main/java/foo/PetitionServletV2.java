package foo;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;

import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.EntityNotFoundException;
import com.google.appengine.api.datastore.FetchOptions;
import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.datastore.KeyFactory;
import com.google.appengine.api.datastore.KeyRange;
import com.google.appengine.api.datastore.PreparedQuery;
import com.google.appengine.api.datastore.Query;
import com.google.appengine.api.datastore.Query.FilterOperator;
import com.google.appengine.api.datastore.Query.FilterPredicate;

@WebServlet(name = "PetServletV2", urlPatterns = { "/petition-v2" })
public class PetitionServletV2 extends HttpServlet {

	@Override
	public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {

		response.setContentType("text/html");
		response.setCharacterEncoding("UTF-8");

		Random r = new Random();
		DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();

        Entity[] plist=new Entity[10];
        Entity[] ulist=new Entity[50];

		// Create petition
		for (int i = 0; i < plist.length; i++) {
			Entity e = new Entity("Petition");
			int auteur = r.nextInt(ulist.length);
			e.setProperty("auteur", "U"+ auteur);
			e.setProperty("dateCreation", new Date());
			e.setProperty("description", "bla bla bla");
			e.setProperty("nbSignatures", 5);
			
			// Create random tags
			HashSet<String> ftags = new HashSet<String>();
			while (ftags.size() < 5) {
				ftags.add("T" + r.nextInt(10));
			}
			e.setProperty("tags", ftags);
			
            plist[i]=e;
			datastore.put(e);
			response.getWriter().print("<li> created post:" + e.getKey() + "<br>");
		}
        // Create users
		for (int i = 0; i < ulist.length; i++) {
			Entity e = new Entity("User", "U" + i );
            e.setProperty("name", "U"+i);

            // Sign Random Petition
			HashSet<String> pets = new HashSet<String>();
			for (int y=0;y<5;y++) {
                int rpet=r.nextInt(plist.length);
				pets.add("P" + rpet);
			}
            e.setProperty("signed", pets);

            ulist[i]=e;
			datastore.put(e);
			response.getWriter().print("<li> created post:" + e.getKey() + "<br>");
        }
	}
}