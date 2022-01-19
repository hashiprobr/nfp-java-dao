nfp-java-dao
============

**Not-For-Production DAO framework based on Cloud Firestore and Storage.**


Quick setup
-----------

1) Open the [Firebase console](https://console.firebase.google.com/).

2) Create a new Firebase project.

3) Create a **Firestore Database** in the project.

4) Create a **Storage** in the project. Take note of its URL.

5) Open the project settings (⚙) and download a new private key:

   * open the **Service Accounts** tab;

   * click on the **Generate New Private Key** button and confirm.

6) Rename the private key to `main.json` and move it to the repository root.
   **Never commit this file, as it is private.** All JSON files are in the
   `.gitignore` file by default.

7) Open the repository as an Eclipse project.


Quick start
-----------

### Creating a storable object

Any instance of a subclass of `FirestoreObject` can be stored. Being a subclass
of `FirestoreObject` only requires a method `key` that receives no arguments and
returns a string.

This method is required because Firestore documents must be uniquely identified
by a string. If your class already has an unique field, it can simply return
this field, converting to string if necessary.

In the example below, we assume that the `id` of an user is unique.

``` java
public class User extends FirebaseObject {
	private int id;
	private String name;

    public User(int id, String name) {
        this.id = id;
        this.name = name;
    }

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

	@Override
	public String key() {
		return Integer.toString(id);
	}
}
```

### Creating a DAO for the object

A DAO for an `User`, as defined above, is an instance of a subclass of
`FirestoreDAO`, with `User` as its parameter. The super constructor must receive
the name of the Firestore collection where you want the objects to be stored.

In the example below, we define that the collection is named `"users"`.

``` java
public class UserDAO extends FirestoreDAO<User> {
    public UserDAO() {
        super("users");
    }
}
```

### Opening a Firebase connection

You need to open a Firebase connection to use a DAO, but you only need to do
this once.

In the example below, we assume that `"main.json"` is the private key and
`"example-12345.appspot.com"` is the aforementioned URL of the Storage.

``` java
public static void main(String[] args) {
    Firebase firebase = Firebase.buildInstance("main.json", "example-12345.appspot.com");
    firebase.connect();
}
```

### Using the DAO for CRUD operations

``` java
UserDAO dao = new UserDAO();
```

``` java
User user = new User(123, "John Doe");
dao.create(user);
```

``` java
User user = dao.retrieve("123");
```

``` java
User user = new User(123, "Jane Doe");
dao.update(user);
```

``` java
dao.delete("123");
```


Objects with automatic keys
---------------------------

If the object is the instance of a subclass of `AutokeyFirestoreObject`, the
method `key` is not necessary. Instead, `AutokeyFirestoreObject` provides its
own `getKey` and `setKey` methods, which are used automatically.

In the example below, we assume that no field of a group is unique.

``` java
public class Group extends AutokeyFirebaseObject {
	private String name;

    public Group(String name) {
        this.name = name;
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
public class GroupDAO extends FirestoreDAO<Group> {
    public GroupDAO() {
        super("groups");
    }
}
```

Using the DAO is also the same, with the added observation that `create` returns
the key that was created automatically.

``` java
GroupDAO dao = new GroupDAO();
```

``` java
Group group = new Group("Owners");
String key = dao.create(group);
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


Batch operations
----------------

All four CRUD methods have a list version.

* `public List<String> create(List<T> values)`

* `public List<T> retrieve(List<String> keys)`

* `public void update(List<T> values)`

* `public void delete(List<String> keys)`


File operations
---------------

All four CRUD methods have a file version.

* `public String create(String key, String name, InputStream stream)`

* `public String retrieve(String key, String name)`

* `public String update(String key, String name, InputStream stream)`

* `public void delete(String key, String name)`

These methods are supposed to be used for a file that is directly related to a
storable object. Therefore, the file path is `<collection>/<key>/<name>` and it
is expected that the key is from this object.


Query operations
----------------

The `retrieve` and `delete` methods have a query version.

* `public List<T> retrieve(FirebaseDAO<T>.Selection selection)`

* `public void delete(String key, String name)`

To build a selection, you only need to call a select method from the DAO. This
method can be chained with the `orderBy`, `descending`, `offset`, and `limit`
methods for query constraints.

``` java
UserDAO.Selection selection = dao.select().orderBy("name").descending();
List<User> users = dao.retrieve(selection);
```

``` java
GroupDAO.Selection selection = dao.select().offset(10).limit(20);
dao.delete(selection)
```

All the select methods available are listed below.

* `select()`: all objects.

* `select(List<String> keys)`: objects with keys in the list.

* `selectExcept(List<String> keys)`: objects with keys not in the list.

* `selectWhereIn(String name, List<Object> values)`: objects with the value of
  field `name` in the list.

* `selectWhereNotIn(String name, List<Object> values)`: objects with the value
  of field `name` not in the list.

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
  field `name` containing `value`. *(assumes the field is array)*

* `selectWhereContainsAny(String name, Object value)`: objects with the value of
  field `name` containing `value`. *(assumes the field is array)*
