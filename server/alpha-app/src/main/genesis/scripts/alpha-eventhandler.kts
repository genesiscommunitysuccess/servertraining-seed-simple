/**
 * System              : Genesis Business Library
 * Sub-System          : multi-pro-code-test Configuration
 * Version             : 1.0
 * Copyright           : (c) Genesis
 * Date                : 2022-03-18
 * Function : Provide event handler config for multi-pro-code-test.
 *
 * Modification History
 */
import genesis.global.message.event.*
import genesis.global.eventhandler.validate.*
import genesis.global.eventhandler.commit.*
import global.genesis.jackson.core.GenesisJacksonMapper
import java.io.File
import java.time.LocalDate
import global.genesis.commons.standards.GenesisPaths


eventHandler {

    eventHandler<Trade, CustomTradeEventReply>(name = "TRADE_INSERT") {

        onException{ event, throwable ->
            CustomTradeEventReply.TradeNck("ERROR: ${throwable.message}")
        }

        onValidate { event ->
            ValidateTrade.validateInsert(event,entityDb)
            CustomTradeEventReply.ValidationTradeAck()
        }
        onCommit { event ->
            val trade = event.details
            entityDb.insert(trade)
            CustomTradeEventReply.TradeAck("TRADE_INSERT_ACK")

        }
    }

    eventHandler<Trade>(name = "TRADE_MODIFY", transactional = true) {
        onValidate { event ->
            val message = event.details
            verify {
                entityDb hasEntry Counterparty.ById(message.counterpartyId.toString())
                entityDb hasEntry Instrument.byId(message.instrumentId)
            }
            ack()
        }
        onCommit { event ->
            val trade = event.details
            entityDb.modify(trade)
            ack()
        }
    }

    eventHandler<Trade>(name = "TRADE_DELETE", transactional = true) {
        onCommit { event ->
            val trade = event.details
            entityDb.delete(trade)
            ack()
        }
    }
    eventHandler<Trade>(name="TRADE_UPSERT"){
        schemaValidation = false
        onValidate {
            ValidateTrade.validateUpsert(it,entityDb)
            ack()
        }
        onCommit {
            CommitTrade.upsert(it,entityDb)
            ack()
        }
    }

    eventHandler<Counterparty>(name = "COUNTERPARTY_INSERT") {
        schemaValidation = false
        onCommit { event ->
            entityDb.insert(event.details)
            ack()
        }
    }

    eventHandler<Counterparty>(name = "COUNTERPARTY_MODIFY") {
        onCommit { event ->
            entityDb.modify(event.details)
            ack()
        }
    }

    eventHandler<Counterparty>(name = "COUNTERPARTY_DELETE") {
        onValidate{
            ValidateCounterparty.validateDelete(it, entityDb)
            ack()
        }
        onCommit { event ->
            entityDb.delete(event.details)
            ack()
        }
    }
    eventHandler<Counterparty>(name="COUNTERPARTY_UPSERT"){
        schemaValidation = false

        onCommit {
            CommitCounterparty.upsert(it,entityDb)
            ack()
        }
    }
    eventHandler<Instrument>(name="INSTRUMENT_UPSERT"){
        schemaValidation = false
        onCommit {
            CommitInstrument.upsert(it,entityDb)
            ack()
        }
    }

    eventHandler<Instrument>(name = "INSTRUMENT_INSERT") {
        schemaValidation = false
        onCommit { event ->
            entityDb.insert(event.details)
            ack()
        }
    }

    eventHandler<Instrument>(name = "INSTRUMENT_MODIFY") {
        onCommit { event ->
            entityDb.modify(event.details)
            ack()
        }
    }

    eventHandler<Instrument>(name = "INSTRUMENT_DELETE") {
        onValidate{
            ValidateInstrument.validateDelete(it, entityDb)
            ack()
        }
        onCommit { event ->
            entityDb.delete(event.details)
            ack()
        }
    }

    contextEventHandler<Instrument, List<Trade>>(name = "INSTRUMENT_DELETE_CASCADE", transactional = true){
        onValidate{ event ->
            val trades = ValidateInstrument.validateCascadeDelete(event, entityDb)
            validationResult(ack(),trades)
        }

        onCommit{ event, trades ->
            if(trades != null){
                for (trade in trades){
                    LOG.info("Deleting trade ${trade.tradeId}")
                    entityDb.delete(Trade.byId(trade.tradeId))
                }
            }
            entityDb.delete(event.details)
            ack()
        }
    }

    eventHandler<PositionReport>(name = "POSITION_REPORT") {
        onCommit {
            val mapper = GenesisJacksonMapper.csvWriter<TradeView>()
            val today = LocalDate.now().toString()
            val positionReportFolder = File(GenesisPaths.runtime()).resolve("position-minute-report")
            if (!positionReportFolder.exists()) positionReportFolder.mkdirs()

            entityDb.getBulk(TRADE_VIEW)
                .toList()
                .groupBy { it.counterpartyName }
                .forEach { (counterParty, trades) ->
                    val file = positionReportFolder.resolve("${counterParty}_$today.csv")
                    if (file.exists()) file.delete()
                    mapper.writeValues(file).use { it.writeAll(trades) }
                }

            ack()
        }
    }

    eventHandler<TradeStandardization>(transactional = true) {
        onCommit {
            val tradesNegativePrices = entityDb.getBulk(TRADE).toList()
                .filter { it.price < 0 }

            tradesNegativePrices.forEach {
                it.price = 0.0
            }

            entityDb.modifyAll(*tradesNegativePrices.toList().toTypedArray())
            ack()
        }
    }

    eventHandler<TrainingClass>(name = "TEST"){
        schemaValidation = false
        onCommit{
            ack()
        }
    }


}