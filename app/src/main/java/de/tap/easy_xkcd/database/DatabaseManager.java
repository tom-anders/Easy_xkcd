package de.tap.easy_xkcd.database;

import android.content.Context;

import io.realm.DynamicRealm;
import io.realm.FieldAttribute;
import io.realm.Realm;
import io.realm.RealmConfiguration;
import io.realm.RealmMigration;
import io.realm.RealmObjectSchema;
import io.realm.RealmSchema;

public class DatabaseManager {
    private Context context;
    public Realm realm;
    private static final String REALM_DATABASE_LOADED = "pref_realm_database_loaded";
    private static final String HIGHEST_DATABASE = "highest_database_newversion";
    private static final String COMIC_READ = "comic_read";
    private static final String FAVORITES = "favorites";

    private static RealmConfiguration config;

    private class Migration implements RealmMigration {
        @Override
        public void migrate(DynamicRealm realm, long oldVersion, long newVersion) {
            RealmSchema schema = realm.getSchema();
            RealmObjectSchema objectSchema = schema.get("RealmComic");
            if (!objectSchema.hasField("altText")) { //Add the altText field which wasn't there in the old version!
                objectSchema.addField("altText", String.class);
            }
            if (!objectSchema.hasField("isOffline")) {
                objectSchema.addField("isOffline", boolean.class);
            }

            if (!schema.contains("Article")) {
                RealmObjectSchema articleSchema = schema.create("Article")
                        .addField("number", int.class, FieldAttribute.PRIMARY_KEY)
                        .addField("title", String.class)
                        .addField("thumbnail", String.class)
                        .addField("favorite", boolean.class)
                        .addField("read", boolean.class)
                        .addField("offline", boolean.class);
            }

        }

        @Override
        public int hashCode() {
            return 37;
        }

        @Override
        public boolean equals(Object o) {
            return (o instanceof Migration);
        }
    }

    // Just here while we have to do the migration from the legacy realm database
    @Deprecated
    public DatabaseManager(Context context) {
        if (config == null) {
            config = new RealmConfiguration.Builder(context)
                    .schemaVersion(3) // Must be bumped when the schema changes
                    .migration(new Migration()) // Migration to run
                    .build();
            Realm.setDefaultConfiguration(config);
        }

        this.context = context;
        realm = Realm.getDefaultInstance();
    }
}



