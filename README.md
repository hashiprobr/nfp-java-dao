nfp-java-dao
============

**[Not-For-Production](https://github.com/hashiprobr/nfp) DAO framework based on
Cloud Firestore and Storage.**

If your project is a class assignment or a simple prototype, use this framework
to store objects and files remotely with minimum boilerplate. All you need is a
Firebase project and a couple of lines of code.


Quick setup
-----------

1) Open the [Firebase console](https://console.firebase.google.com/).

2) Create a new Firebase project.

3) Create a **Firestore Database** in the project.

4) Open the project settings (⚙) and download a credentials file:

   * open the **Service Accounts** tab;

   * click on the **Generate New Private Key** button and confirm.

5) Rename the credentials file to something simpler like `main.json` and move it
   to somewhere safe. **Never commit this file, as it is private.**

6) [Add the framework to your
   project.](https://mvnrepository.com/artifact/io.github.hashiprobr/nfp-dao)


Quick start
-----------

### Creating a storable object

Being a storable object only requires annotating one of its class fields with
`Key`. This is required because Firestore documents must be uniquely identified.

Firestore only accepts strings as keys. If the annotated field is not a string,
the actual value is obtained from `toString()`.

In the example below, we assume that the `id` of an user is unique.

``` java
import br.pro.hashi.nfp.dao.annotation.Key;

public class User {
    @Key
    private int id;
    private String name;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
```

### Creating a DAO for the object

A DAO for an `User`, as defined above, is an instance of a subclass of `DAO`,
with `User` as its parameter. The super constructor must receive the name of the
Firestore collection where you want the objects to be stored.

In the example below, we define that the collection is named `"users"`.

``` java
import br.pro.hashi.nfp.dao.DAO;

public class UserDAO extends DAO<User> {
    public UserDAO() {
        super("users");
    }
}
```

### Opening a Firebase connection

You need to open a Firebase connection to use a DAO, but you only need to do
this once.

In the example below, we assume that `"main.json"` is the path to the
credentials file.

``` java
import br.pro.hashi.nfp.dao.Firebase;
import br.pro.hashi.nfp.dao.FirebaseManager;

public static void main(String[] args) {
		FirebaseManager manager = Firebase.manager();
    Firebase firebase = manager.getFromCredentials("main.json");
    firebase.connect();
}
```

### Using the DAO for CRUD operations

The operations use the existent fields, getters, and setters as a reference for
what should be stored.

``` java
UserDAO dao = new UserDAO();
```

``` java
User user = new User(123, "John Doe");
dao.create(user);
```

``` java
User user = dao.retrieve(123);
```

``` java
User user = new User(123, "Jack Doe");
dao.update(user);
```

``` java
dao.delete(123);
```


Objects with automatic keys
---------------------------

If none of the class fields are unique, it is possible to add a new string field
and annotate it with `@Autokey`. This will make it be generated automatically.

In the example below, we assume that the `name` of a group is not unique, so it
cannot be used as the key.

``` java
import br.pro.hashi.nfp.dao.annotation.Autokey;

public class Group {
    @Autokey
    private String key;
    private String name;

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
```

Creating a DAO in this case is exactly the same.

``` java
public class GroupDAO extends DAO<Group> {
    public GroupDAO() {
        super("groups");
    }
}
```

Using the DAO is also the same, with the added observation that `create` writes
the generated key to the object.

``` java
GroupDAO dao = new GroupDAO();
```

``` java
Group group = new Group("Owners");
String key = group.getKey(); // before creation, returns null
dao.create(group);
String key = group.getKey(); // after creation, returns the generated key
```

``` java
Group group = dao.retrieve(key);
```

``` java
Group group = new Group("Admins");
group.setKey(key);
dao.update(group);
```

``` java
dao.delete(key);
```


Partial update
--------------

If you only want to update specific fields, you can pass a `Map<String, Object>`
to `update` instead of an instance of the class.

``` java
Map<String, Object> fields = new HashMap<>();
fields.put("key", key);
fields.put("name", "Admins");
dao.update(fields);
```


Query operations
----------------

The `retrieve` and `delete` methods have a query version.

* `public List<T> retrieve(Selection selection)`

* `public void delete(Selection selection)`

To build a selection, you only need to call a select method from the DAO. This
method can be chained with the `orderBy`, `limit`, and `limitToLast` methods for
query constraints.

The `orderBy` method accepts an option boolean to indicate if you want the order
to be descending.

``` java
Selection selection = dao.selectAll().orderBy("name", true);
List<User> users = dao.retrieve(selection);
```

``` java
Selection selection = dao.select().offset(10).limit(20);
dao.delete(selection)
```

All select methods available are listed below.

* `selectAll()`: all objects.

* `selectWhereEqualTo(String name, Object value)`: objects with the value of
  field `name` equal to `value`.

* `selectWhereNotEqualTo(String name, Object value)`: objects with the value of
  field `name` not equal to `value`.

* `selectWhereLessThan(String name, Object value)`: objects with the value of
  field `name` less than `value`. *(assumes the field is sortable)*

* `selectWhereLessThanOrEqualTo(String name, Object value)`: objects with the
  value of field `name` less than or equal to `value`. *(assumes the field is
  sortable)*

* `selectWhereGreaterThan(String name, Object value)`: objects with the value of
  field `name` greater than `value`. *(assumes the field is sortable)*

* `selectWhereGreaterThanOrEqualTo(String name, Object value)`: objects with the
  value of field `name` greater than or equal to `value`. *(assumes the field is
  sortable)*

* `selectWhereContains(String name, Object value)`: objects with the value of
  field `name` containing `value`. *(assumes the field is an array)*

* `selectWhereContainsAny(String name, Object value)`: objects with the value of
  field `name` containing `value`. *(assumes the field is an array)*

* `selectWhereIn(String name, List<?> values)`: objects with the value of
  field `name` in the list.

* `selectWhereNotIn(String name, List<?> values)`: objects with the value
  of field `name` not in the list.


File operations
---------------

The `create` and `update` methods accept an optional `Map<String, InputStream>`
that can be used to upload files. In order to use this parameter, the class must
have string fields annotated with `@File`.

``` java
import br.pro.hashi.nfp.dao.annotation.Autokey;
import br.pro.hashi.nfp.dao.annotation.File;

public class Entry {
	@Autokey
	private String key;
	@File
	private String photo;

	public String getKey() {
		return key;
	}

	public void setKey(String key) {
		this.key = key;
	}

	public String getPhoto() {
		return photo;
	}

	public void setPhoto(String photo) {
		this.photo = photo;
	}
}
```

If such fields exist, you can use their names as the keys of the parameter.

``` java
EntryDAO dao = new EntryDAO();

Entry entry = new Entry();

Map<String, InputStream> streams = new HashMap<>();
streams.put("photo", new FileInputStream("something.png"));

dao.create(entry, streams);
```

In the example above, the DAO will:

* upload the file to Firebase Storage;

* generate a public URL to access the file;

* store the URL in the object;

* save the object as usual.

This obviously assumes that the file can be public. It is made specifically to
create static resources.

Please note that, due to some limitations, **updating a file changes its URL**.


Custom serialization
--------------------

Firestore cannot store arbitrary types, so in some cases you need to "translate"
the values to something it supports. For example, let's suppose the `Entry`
class has also a `LocalDateTime` field.

``` java
import java.time.LocalDateTime;

import br.pro.hashi.nfp.dao.annotation.Autokey;
import br.pro.hashi.nfp.dao.annotation.File;

public class Entry {
	@Autokey
	private String key;
	private LocalDateTime timestamp;
	@File
	private String photo;

	public String getKey() {
		return key;
	}

	public void setKey(String key) {
		this.key = key;
	}

	public LocalDateTime getTimestamp() {
		return timestamp;
	}

	public void setTimestamp(LocalDateTime timestamp) {
		this.timestamp = timestamp;
	}

	public String getPhoto() {
		return photo;
	}

	public void setPhoto(String photo) {
		this.photo = photo;
	}
}
```

Firestore does not support `LocalDateTime`, so an attempt to create an instance
of this class will throw an error. To solve this, we can write an adapter class
to override the getter and the setter.

``` java
import br.pro.hashi.nfp.dao.Adapter;

public class EntryAdapter extends Adapter<Entry> {
	public long getTimestamp() {
		return that.getTimestamp().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
	}

	public void setTimestamp(long timestamp) {
		that.setTimestamp(LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.systemDefault()));
	}
}
```

This class can then be passed as an optional parameter of `create`, `retrieve`,
and `update`.

``` java
dao.create(entry, EntryAdapter.class);
```

``` java
Entry entry = dao.retrieve(key, EntryAdapter.class);
```

``` java
dao.update(entry, EntryAdapter.class);
```
