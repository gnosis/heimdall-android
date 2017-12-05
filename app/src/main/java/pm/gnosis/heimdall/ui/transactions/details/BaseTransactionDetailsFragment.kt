package pm.gnosis.heimdall.ui.transactions.details

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.TextView
import com.gojuno.koptional.Optional
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.rxkotlin.plusAssign
import kotlinx.android.synthetic.main.layout_safe_spinner_item.view.*
import pm.gnosis.heimdall.R
import pm.gnosis.heimdall.common.utils.Result
import pm.gnosis.heimdall.data.repositories.models.Safe
import pm.gnosis.heimdall.ui.base.BaseFragment
import pm.gnosis.models.Transaction
import pm.gnosis.utils.asEthereumAddressString
import java.math.BigInteger
import javax.inject.Inject


abstract class BaseTransactionDetailsFragment : BaseFragment(), AdapterView.OnItemSelectedListener {

    @Inject
    lateinit var baseViewModel: BaseTransactionDetailsContract

    private val adapter by lazy {
        // Adapter should only be created if we need it
        SafesSpinnerAdapter(context!!)
    }

    private var spinner: Spinner? = null

    abstract fun observeTransaction(): Observable<Result<Transaction>>
    abstract fun observeSafe(): Observable<Optional<BigInteger>>
    abstract fun inputEnabled(enabled: Boolean)

    protected fun setupSafeSpinner(spinner: Spinner, defaultSafe: BigInteger?) {
        this.spinner = spinner
        spinner.adapter = adapter
        disposables += baseViewModel.observeSafes(defaultSafe)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::setSpinnerData, {
                    val safes = defaultSafe?.let { listOf(Safe(it)) } ?: emptyList()
                    setSpinnerData(BaseTransactionDetailsContract.State(defaultSafe, safes))
                })
    }

    open fun selectedSafeChanged(safe: Safe?) {}

    private fun setSpinnerData(state: BaseTransactionDetailsContract.State) {
        this.spinner?.let {
            adapter.clear()
            adapter.addAll(state.safes)
            adapter.notifyDataSetChanged()
            it.onItemSelectedListener = this
        }
    }

    override fun onItemSelected(parent: AdapterView<*>, view: View, position: Int, id: Long) {
        val safe = parent.adapter.getItem(position) as Safe
        selectedSafeChanged(safe)
    }

    override fun onNothingSelected(parent: AdapterView<*>) {
        selectedSafeChanged(null)
    }

    private class SafesSpinnerAdapter(context: Context) : ArrayAdapter<Safe>(context, R.layout.layout_safe_spinner_item, ArrayList()) {
        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val view = getDropDownView(position, convertView, parent)
            view.setPadding(0, 0, 0, 0)
            return view
        }

        override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val viewHolder = getViewHolder(convertView, parent)
            val item = getItem(position)
            viewHolder.titleText.text = item.name
            viewHolder.subtitleText.text = item.address.asEthereumAddressString()
            return viewHolder.itemView
        }

        private fun getViewHolder(convertView: View?, parent: ViewGroup?): ViewHolder {
            val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.layout_safe_spinner_item, parent, false)
            return (view.tag as? ViewHolder) ?: createAndSetViewHolder(view)
        }

        private fun createAndSetViewHolder(view: View): ViewHolder {
            val viewHolder = ViewHolder(view, view.layout_safe_spinner_item_name, view.layout_safe_spinner_item_address)
            view.tag = viewHolder
            return viewHolder
        }

        data class ViewHolder(val itemView: View, val titleText: TextView, val subtitleText: TextView)
    }


}