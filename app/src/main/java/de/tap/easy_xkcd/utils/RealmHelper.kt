package de.tap.easy_xkcd.utils

import io.realm.Realm
import io.realm.RealmObject
import io.realm.RealmResults

fun <T> returnWithRealm(f: (realm: Realm) -> T): T {
    val realm = Realm.getDefaultInstance()
    val result = f(realm)
    realm.close()
    return result
}

fun doWithRealm(f: (Realm) -> Unit) {
    val realm = Realm.getDefaultInstance()
    f(realm)
    realm.close()
}

fun <T: RealmObject> copyFromRealm(f: (Realm) -> T? ): T? {
    val realm = Realm.getDefaultInstance()

    var result = f(realm)
    result?.let {
        result = realm.copyFromRealm(result)
    }

    realm.close()
    return result
}

fun <T: RealmObject> copyResultsFromRealm(f: (Realm) -> RealmResults<T> ): List<T> {
    val realm = Realm.getDefaultInstance()
    val result = realm.copyFromRealm(f(realm))
    realm.close()
    return result
}

fun <T: RealmObject> copyToRealmOrUpdate(obj: T) {
    val realm = Realm.getDefaultInstance()
    realm.executeTransaction { realm.copyToRealmOrUpdate(obj) }
    realm.close()
}