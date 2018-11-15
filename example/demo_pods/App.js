import React, { Component, Fragment } from 'react';
import { StyleSheet, Text, View, Button, Modal, ActivityIndicator } from 'react-native';
import kin from 'react-native-kin';
import Loader from './Loader';

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#fff',
    alignItems: 'center',
    justifyContent: 'center',
  },
});

let credentials;
try {
  credentials = require('./config.json');
} catch (e) {
  credentials = require('./configDefault.json');
}
credentials.debug = __DEV__;

const userId = '12-34-61';
// const userId = '2828282828282828';

export default class App extends Component {

  constructor(props) {
    super(props);

    this.state = {
      loading: false,
      loadingText: undefined,
      balance: 0,
    };
  }

  componentDidMount() {

  }

  showLoader(loadingText) {
    this.setState({ loading: true, loadingText })
  }

  hideLoader() {
    this.setState({ loading: false, loadingText: null })
  }

  async start() {
    // const isOnboarded = await kin.isOnboarded();
    // console.log({ isOnboarded });

    kin.setCredentials(credentials)
      .then((credentials) => {
        console.log('setCredentials', credentials);
        this.initEventHandler();
      })
      .then(() => kin.start({
        userId: userId,
        environment: kin.ENVIRONMENT_BETA,
      }))
      .then((start) => {
        console.log('start', start);
      })
      .catch((error) => {
        console.error('start', error);
      });
  }

  launchMarketplace() {
    this.addSpendOffer('any-spend-offer-1');
    kin.launchMarketplace()
      .then((marketPlace) => {
        console.log({ marketPlace });
      })
      .catch((error) => {
        console.error('launchMarketplace', error);
      });
  }

  getWalletAddress() {
    this.showLoader('loading...');
    kin.getWalletAddress()
      .then((walletAddress) => {
        console.log({ walletAddress });
        alert(`walletAddress: ${walletAddress}`);
      })
      .catch((error) => {
        console.error('getWalletAddress', error);
      })
      .finally(() => this.hideLoader());
  }

  getCurrentBalance() {
    this.showLoader('get balance...');
    kin.getCurrentBalance()
      .then((currentBalance) => {
        console.log({ currentBalance });
        alert(`currentBalance: ${currentBalance}`);
      })
      .catch((error) => {
        console.error('getCurrentBalance', error);
      })
      .finally(() => this.hideLoader());
  }

  earn(offerId) {
    this.showLoader('earning...');
    kin.earn({
      offerId,
      offerAmount: 100,
      offerTitle: 'This is a demo earn',
      offerDescription: 'earn: ' + offerId,
      recipientUserId: userId,
    })
      .then((jwtConfirmation) => {
        console.log(`https://jwt.io/#debugger-io?token=${jwtConfirmation}`);
        alert(`earn: ${jwtConfirmation}`);
      })
      .catch((error) => {
        console.error('earn', error);
        console.error('earn', error.userInfo);
      })
      .finally(() => this.hideLoader());
  }

  spend(offerId) {
    this.showLoader('spending...');
    kin.spend({
      offerId,
      offerAmount: 10,
      offerTitle: 'This is a demo spend',
      offerDescription: 'spend: ' + offerId,
      recipientUserId: userId,
    })
      .then((jwtConfirmation) => {
        console.log(`https://jwt.io/#debugger-io?token=${jwtConfirmation}`);
        alert(`spend: ${jwtConfirmation}`);
      })
      .catch((error) => {
        console.error('spend', error);
      })
      .finally(() => this.hideLoader());
  }

  payToUser(toUserId, toUsername) {
    this.showLoader('paying...');
    kin.payToUser({
      offerId: `pay-to-user-${userId}-${toUserId}`,
      offerAmount: 10,
      toUserId,
      toUsername,
      fromUsername: 'tester'
    })
      .then((jwtConfirmation) => {
        console.log(`https://jwt.io/#debugger-io?token=${jwtConfirmation}`);
        alert(`payToUser: ${jwtConfirmation}`);
      })
      .catch((error) => {
        console.error('payToUser', error);
      })
      .finally(() => this.hideLoader());
  }

  addSpendOffer(offerId) {
    kin.addSpendOffer({
      offerId: offerId,
      offerAmount: 10,
      offerTitle: 'offer title ' + offerId,
      offerDescription: 'offer description ' + offerId,
      offerImageURL: 'https://via.placeholder.com/300x225',
      isModal: true,
    })
      .then((success) => {
        console.log(`addSpendOffer: ${offerId}: ${success}`);
      })
      .catch((error) => {
        console.error('addSpendOffer', error);
      });
  }

  removeSpendOffer(offerId) {
    kin.removeSpendOffer({
      offerId: offerId,
    })
      .then((success) => {
        console.log(`removeSpendOffer: ${offerId}: ${success}`);
      })
      .catch((error) => {
        console.error('removeSpendOffer', error);
      });
  }

  initEventHandler() {
    kin.events.addListener('onNativeOfferClicked', (offer) => {
      console.log('Listener: offer clicked', offer);
    });
    kin.events.addListener('onBalanceChanged', (balance) => {
      console.log('Listener: amount changed', balance);
      this.setState({ balance: balance });
    });
  }

  componentWillMount() {
    console.log('kin', kin);

    this.start();
  }

  render() {
    const { loading, loadingText, balance } = this.state;

    return (
      <Fragment>
        <View style={styles.container}>
          <Text>UserID: {userId}</Text>
          <Text>Balance: {balance}</Text>
          <Button onPress={() => this.launchMarketplace()} title="Launch Marketplace" />
          <Button onPress={() => this.getWalletAddress()} title="Get Address" />
          <Button onPress={() => this.getCurrentBalance()} title="Get Balance" />
          {/*<Button*/}
          {/*onPress={() => this.earn('payment-0' + Math.round(Math.random() * 1000))}*/}
          {/*title="Request Payment Random"*/}
          {/*/>*/}
          <Button onPress={() => this.earn('fail-05')} title="Earn Fails" />
          <Button onPress={() => this.earn('earn-01')} title="Earn 2" />
          <Button onPress={() => this.earn('earn-02')} title="Earn 3" />
          <Button onPress={() => this.spend('spend-0' + Math.round(Math.random() * 1000))} title="Spend 1" />
          <Button onPress={() => this.payToUser('2828282828282828', 'phips28')} title="Pay to user" />
          <Button onPress={() => this.addSpendOffer('spend-offer-1')} title="Add spend offer 1" />
          <Button onPress={() => this.removeSpendOffer('spend-offer-1')} title="Remove spend offer 1" />
        </View>
        {loading && <Loader loading={loading} text={loadingText} />}
      </Fragment>
    );
  }
}
