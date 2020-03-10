package com.rdt.urbleterm

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.FragmentManager
import kotlinx.android.synthetic.main.activity_main.*

class ActivityMain : AppCompatActivity(), FragmentManager.OnBackStackChangedListener {

    //
    // LIFECYCLE
    //
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(v_toolbar)

        MyConfig.MY_ACTION_DISCONNECT = "$packageName.DISCONNECT"

        supportFragmentManager.addOnBackStackChangedListener(this)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction().add(R.id.v_frag, FragDevList(), "device").commit()
        } else {
            onBackStackChanged()
        }
    }

    //
    // FRAGMENT MANAGER
    //
    override fun onBackStackChanged() {
        supportActionBar!!.setDisplayHomeAsUpEnabled(supportFragmentManager.backStackEntryCount > 0)
    }

    //
    // ACTION BAR
    //
    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

}

/* EOF */