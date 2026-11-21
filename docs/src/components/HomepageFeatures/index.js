import Link from '@docusaurus/Link';
import Heading from '@theme/Heading';
import styles from './styles.module.css';

const FeatureList = [
  {
    title: 'Too expensive? Switch.',
    Svg: require('@site/static/img/hop.svg').default,
    description: (
      <>
        The provider is a Maven profile. Build for another cloud, deploy,
        compare the bill. Your functions stay exactly as they are.
      </>
    ),
  },
  {
    title: 'No cloud SDK in your code',
    Svg: require('@site/static/img/java.svg').default,
    description: (
      <>
        Functions are plain Java methods with <code>@Function</code> and a
        trigger. Handler classes, Terraform and API descriptions are generated
        at compile time, so nothing in your code ties you to one vendor.
      </>
    ),
  },
  {
    title: 'EU or on-premise, just another target',
    Svg: require('@site/static/img/relax.svg').default,
    description: (
      <>
        Deploy to an EU region of your cloud, to Scaleway, to any Kubernetes
        with Knative, or run the Spring Boot target on your own servers.
        Override a generated template in your project, or add a target of your
        own through the <Link to="/docs/guides/customizing-targets">SPI</Link>.
      </>
    ),
  },
];

function Feature({Svg, title, description}) {
  return (
    <li className={styles.card}>
      <Svg className={styles.featureSvg} aria-hidden="true" />
      <Heading as="h3" className={styles.cardTitle}>{title}</Heading>
      <p className={styles.cardText}>{description}</p>
    </li>
  );
}

export default function HomepageFeatures() {
  return (
    <section className={styles.features} aria-labelledby="why-title">
      <div className="container">
        <Heading as="h2" id="why-title" className={styles.title}>Why Cloudhopper</Heading>
        <ul className={styles.grid}>
          {FeatureList.map((props) => (
            <Feature key={props.title} {...props} />
          ))}
        </ul>
      </div>
    </section>
  );
}
